package dev.horbatiuk.timecapsule.service.security;

import dev.horbatiuk.timecapsule.exception.ConflictException;
import dev.horbatiuk.timecapsule.exception.NotFoundException;
import dev.horbatiuk.timecapsule.persistence.UserRepository;
import dev.horbatiuk.timecapsule.persistence.dto.security.AuthenticationRequestDTO;
import dev.horbatiuk.timecapsule.persistence.dto.security.RegisterRequestDTO;
import dev.horbatiuk.timecapsule.persistence.dto.user.UserDTO;
import dev.horbatiuk.timecapsule.persistence.entities.RefreshToken;
import dev.horbatiuk.timecapsule.persistence.entities.User;
import dev.horbatiuk.timecapsule.persistence.entities.VerificationToken;
import dev.horbatiuk.timecapsule.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserVerificationService userVerificationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void register_WhenEmailAlreadyExists_ThrowsConflictException() {
        RegisterRequestDTO dto = new RegisterRequestDTO("test@example.com", "name", "password");
        when(userRepository.findUserByEmail(dto.getEmail())).thenReturn(Optional.of(new User()));

        assertThrows(ConflictException.class, () -> authService.register(dto));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_WhenValid_SavesUserAndAddsToken() throws ConflictException {
        RegisterRequestDTO dto = new RegisterRequestDTO("test@example.com", "John", "password");
        when(userRepository.findUserByEmail(dto.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(dto.getPassword())).thenReturn("encoded-password");

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("mocked-refresh-token");
        when(refreshTokenService.createOrUpdateRefreshToken(dto.getEmail())).thenReturn(refreshToken);

        authService.register(dto);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        verify(userVerificationService).addToken(any(VerificationToken.class));
        verify(jwtService).generateToken(any(CustomUserDetails.class));
        verify(refreshTokenService).createOrUpdateRefreshToken(dto.getEmail());

        User savedUser = userCaptor.getValue();
        assertEquals(dto.getEmail(), savedUser.getEmail());
        assertEquals(dto.getName(), savedUser.getName());
        assertEquals("encoded-password", savedUser.getPassword());
    }

    @Test
    void authenticate_WhenUserNotFound_ThrowsNotFoundException() {
        AuthenticationRequestDTO request = new AuthenticationRequestDTO("email@example.com", "password");
        when(userRepository.findUserByEmail(request.getEmail())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> authService.authenticate(request));
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void authenticate_WhenUserExists_GeneratesTokens() throws NotFoundException {
        AuthenticationRequestDTO request = new AuthenticationRequestDTO("email@example.com", "password");
        User user = User.builder().email(request.getEmail()).build();
        when(userRepository.findUserByEmail(request.getEmail())).thenReturn(Optional.of(user));

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("mocked-refresh-token");
        when(refreshTokenService.createOrUpdateRefreshToken(request.getEmail())).thenReturn(refreshToken);

        authService.authenticate(request);

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtService).generateToken(any(CustomUserDetails.class));
        verify(refreshTokenService).createOrUpdateRefreshToken(request.getEmail());
    }

    @Test
    void getUserInfo_WhenUserNotFound_ThrowsNotFoundException() {
        String email = "notfound@example.com";
        when(userRepository.findUserByEmail(email)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> authService.getUserInfo(email));
    }

    @Test
    void getUserInfo_WhenUserFound_ReturnsUserDTO() throws NotFoundException {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .name("John Doe")
                .build();
        when(userRepository.findUserByEmail(user.getEmail())).thenReturn(Optional.of(user));

        UserDTO result = authService.getUserInfo(user.getEmail());

        assertEquals(user.getId(), result.getId());
        assertEquals(user.getEmail(), result.getEmail());
        assertEquals(user.getName(), result.getName());
    }
}
