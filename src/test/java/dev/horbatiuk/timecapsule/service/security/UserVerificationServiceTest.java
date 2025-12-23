package dev.horbatiuk.timecapsule.service.security;

import dev.horbatiuk.timecapsule.exception.NotFoundException;
import dev.horbatiuk.timecapsule.exception.controller.AppException;
import dev.horbatiuk.timecapsule.persistence.VerificationTokenRepository;
import dev.horbatiuk.timecapsule.persistence.entities.User;
import dev.horbatiuk.timecapsule.persistence.entities.VerificationToken;
import dev.horbatiuk.timecapsule.service.EmailSenderService;
import dev.horbatiuk.timecapsule.service.UserService;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserVerificationServiceTest {

    @InjectMocks
    private UserVerificationService userVerificationService;

    @Mock
    private EmailSenderService emailSenderService;

    @Mock
    private UserService userService;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    private final UUID userId = UUID.randomUUID();
    private final UUID tokenUUID = UUID.randomUUID();
    private final String email = "test@example.com";

    private User user;
    private VerificationToken token;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        String baseUrl = "https://test.app";
        ReflectionTestUtils.setField(userVerificationService, "baseUrl", baseUrl);

        user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .isVerified(false)
                .build();

        token = VerificationToken.builder()
                .token(tokenUUID)
                .user(user)
                .build();
    }

    @Test
    void sendVerificationEmail_ShouldSendEmail_WhenUserNotVerified() throws Exception {
        when(userService.findUserByEmail(email)).thenReturn(user);
        when(verificationTokenRepository.findVerificationTokenByUser(user)).thenReturn(Optional.of(token));

        userVerificationService.sendVerificationEmail(userId, email, false);

        verify(emailSenderService).sendVerificationEmail(eq(email), contains(tokenUUID.toString()));
    }

    @Test
    void sendVerificationEmail_ShouldNotSendEmail_WhenAlreadyVerified() throws Exception {
        user.setVerified(true);

        userVerificationService.sendVerificationEmail(userId, email, true);

        verifyNoInteractions(emailSenderService);
        verifyNoInteractions(verificationTokenRepository);
    }

    @Test
    void sendVerificationEmail_ShouldThrowNotFoundException_WhenTokenNotFound() throws NotFoundException {
        when(userService.findUserByEmail(email)).thenReturn(user);
        when(verificationTokenRepository.findVerificationTokenByUser(user)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class, () ->
                userVerificationService.sendVerificationEmail(userId, email, false));
        assertTrue(ex.getMessage().contains("Verification token not found"));
    }

    @Test
    void sendVerificationEmail_ShouldThrowMessagingException_WhenEmailFails() throws Exception {
        when(userService.findUserByEmail(email)).thenReturn(user);
        when(verificationTokenRepository.findVerificationTokenByUser(user)).thenReturn(Optional.of(token));
        doThrow(new MessagingException("Email failed")).when(emailSenderService)
                .sendVerificationEmail(anyString(), anyString());

        assertThrows(MessagingException.class, () ->
                userVerificationService.sendVerificationEmail(userId, email, false));
    }

    @Test
    void addToken_ShouldSaveToken() {
        userVerificationService.addToken(token);
        verify(verificationTokenRepository).save(token);
    }

    @Test
    void addToken_ShouldThrowAppException_WhenDataAccessFails() {
        doThrow(mock(DataAccessException.class)).when(verificationTokenRepository).save(token);

        AppException ex = assertThrows(AppException.class, () ->
                userVerificationService.addToken(token));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
    }

    @Test
    void verifyToken_ShouldUpdateStatusAndDeleteToken() throws NotFoundException {
        when(verificationTokenRepository.findVerificationTokenByToken(tokenUUID)).thenReturn(Optional.of(token));
        when(userService.findUserByEmail(email)).thenReturn(user);

        userVerificationService.verifyToken(tokenUUID);

        verify(userService).findUserByEmail(email);
        verify(verificationTokenRepository).delete(token);
        assertTrue(user.isVerified());
    }

    @Test
    void verifyToken_ShouldThrow_WhenTokenNotFound() {
        when(verificationTokenRepository.findVerificationTokenByToken(tokenUUID)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class, () ->
                userVerificationService.verifyToken(tokenUUID));
        assertTrue(ex.getMessage().contains("Verification token not found"));
    }

    @Test
    void verifyToken_ShouldThrow_WhenDeleteFails() throws NotFoundException {
        when(verificationTokenRepository.findVerificationTokenByToken(tokenUUID)).thenReturn(Optional.of(token));
        when(userService.findUserByEmail(email)).thenReturn(user);
        doThrow(mock(DataAccessException.class)).when(verificationTokenRepository).delete(token);

        NotFoundException ex = assertThrows(NotFoundException.class, () ->
                userVerificationService.verifyToken(tokenUUID));
        assertTrue(ex.getMessage().contains("Failed to save verification token"));
    }

    @Test
    void updateUserStatus_ShouldDoNothing_WhenAlreadyVerified() throws NotFoundException {
        user.setVerified(true);

        when(userService.findUserByEmail(email)).thenReturn(user);

        userVerificationService.updateUserStatus(email);

        verify(userService).findUserByEmail(email);
        assertTrue(user.isVerified());
    }

    @Test
    void updateUserStatus_ShouldSetVerified_WhenNotYetVerified() throws NotFoundException {
        user.setVerified(false);
        when(userService.findUserByEmail(email)).thenReturn(user);

        userVerificationService.updateUserStatus(email);

        assertTrue(user.isVerified());
    }
}
