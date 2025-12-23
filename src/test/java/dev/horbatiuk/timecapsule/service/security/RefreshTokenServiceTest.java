package dev.horbatiuk.timecapsule.service.security;

import dev.horbatiuk.timecapsule.exception.controller.AppException;
import dev.horbatiuk.timecapsule.persistence.RefreshTokenRepository;
import dev.horbatiuk.timecapsule.persistence.UserRepository;
import dev.horbatiuk.timecapsule.persistence.entities.RefreshToken;
import dev.horbatiuk.timecapsule.persistence.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .name("Test User")
                .build();
    }

    @Test
    void createOrUpdateRefreshToken_ShouldCreateNewToken_IfNotExists() {
        when(userRepository.findUserByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.empty());
        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        when(refreshTokenRepository.save(tokenCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken token = refreshTokenService.createOrUpdateRefreshToken(user.getEmail());

        assertNotNull(token.getToken());
        assertNotNull(token.getExpiryDate());
        assertEquals(user, token.getUser());
    }

    @Test
    void createOrUpdateRefreshToken_ShouldUpdateToken_IfExists() {
        RefreshToken existingToken = RefreshToken.builder()
                .id(1L)
                .user(user)
                .token("old-token")
                .expiryDate(Instant.now().minusSeconds(60))
                .build();

        when(userRepository.findUserByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.of(existingToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken updatedToken = refreshTokenService.createOrUpdateRefreshToken(user.getEmail());

        assertNotEquals("old-token", updatedToken.getToken());
        assertTrue(updatedToken.getExpiryDate().isAfter(Instant.now()));
    }

    @Test
    void createOrUpdateRefreshToken_ShouldThrow_WhenUserNotFound() {
        when(userRepository.findUserByEmail(user.getEmail())).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> refreshTokenService.createOrUpdateRefreshToken(user.getEmail()));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void verifyExpiration_ShouldThrowAndDelete_WhenTokenExpired() {
        RefreshToken token = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().minusSeconds(60))
                .build();

        AppException ex = assertThrows(AppException.class,
                () -> refreshTokenService.verifyExpiration(token));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        verify(refreshTokenRepository).delete(token);
    }

    @Test
    void verifyExpiration_ShouldPass_WhenTokenIsValid() {
        RefreshToken token = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusSeconds(60))
                .build();

        assertDoesNotThrow(() -> refreshTokenService.verifyExpiration(token));
        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    void deleteByUser_ShouldDelete_WhenUserFound() {
        when(userRepository.findUserByEmail(user.getEmail())).thenReturn(Optional.of(user));

        refreshTokenService.deleteByUser(user.getEmail());

        verify(refreshTokenRepository).deleteByUser(user);
    }

    @Test
    void deleteByUser_ShouldThrow_WhenUserNotFound() {
        when(userRepository.findUserByEmail(user.getEmail())).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> refreshTokenService.deleteByUser(user.getEmail()));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void findByToken_ShouldReturnToken_WhenFound() {
        String tokenValue = UUID.randomUUID().toString();
        RefreshToken token = RefreshToken.builder()
                .token(tokenValue)
                .user(user)
                .expiryDate(Instant.now().plusSeconds(3600))
                .build();

        when(refreshTokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(token));

        RefreshToken found = refreshTokenService.findByToken(tokenValue);

        assertEquals(tokenValue, found.getToken());
    }

    @Test
    void findByToken_ShouldThrow_WhenNotFound() {
        String tokenValue = UUID.randomUUID().toString();
        when(refreshTokenRepository.findByToken(tokenValue)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> refreshTokenService.findByToken(tokenValue));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
}
