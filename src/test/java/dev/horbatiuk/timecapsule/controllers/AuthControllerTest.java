package dev.horbatiuk.timecapsule.controllers;

import dev.horbatiuk.timecapsule.exception.ConflictException;
import dev.horbatiuk.timecapsule.exception.NotFoundException;
import dev.horbatiuk.timecapsule.exception.controller.AppException;
import dev.horbatiuk.timecapsule.persistence.dto.security.AuthenticationRequestDTO;
import dev.horbatiuk.timecapsule.persistence.dto.security.AuthenticationResponseDTO;
import dev.horbatiuk.timecapsule.persistence.dto.security.RefreshTokenRequestDTO;
import dev.horbatiuk.timecapsule.persistence.dto.security.RegisterRequestDTO;
import dev.horbatiuk.timecapsule.persistence.entities.RefreshToken;
import dev.horbatiuk.timecapsule.persistence.entities.User;
import dev.horbatiuk.timecapsule.security.CustomUserDetails;
import dev.horbatiuk.timecapsule.service.security.AuthService;
import dev.horbatiuk.timecapsule.service.security.JwtService;
import dev.horbatiuk.timecapsule.service.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void register_shouldReturnOk_whenSuccessful() throws ConflictException {
        RegisterRequestDTO dto = new RegisterRequestDTO();
        when(authService.register(dto)).thenReturn(new AuthenticationResponseDTO());

        ResponseEntity<AuthenticationResponseDTO> response = authController.register(dto);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(authService).register(dto);
    }

    @Test
    void register_shouldThrowAppException_whenConflict() throws ConflictException {
        RegisterRequestDTO dto = new RegisterRequestDTO();
        doThrow(new ConflictException("User already exists")).when(authService).register(dto);

        AppException exception = assertThrows(AppException.class, () -> authController.register(dto));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("User already exists", exception.getMessage());
    }

    @Test
    void authenticate_shouldReturnOk_whenSuccessful() throws NotFoundException {
        AuthenticationRequestDTO request = new AuthenticationRequestDTO();
        when(authService.authenticate(request)).thenReturn(new AuthenticationResponseDTO());

        ResponseEntity<AuthenticationResponseDTO> response = authController.authenticate(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(authService).authenticate(request);
    }

    @Test
    void authenticate_shouldThrowAppException_whenNotFound() throws NotFoundException {
        AuthenticationRequestDTO request = new AuthenticationRequestDTO();
        doThrow(new NotFoundException("User not found")).when(authService).authenticate(request);

        AppException exception = assertThrows(AppException.class, () -> authController.authenticate(request));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("User not found", exception.getMessage());
    }

    @Test
    void refreshToken_shouldReturnNewToken_whenValid() {
        String refreshTokenValue = "valid-refresh-token";
        RefreshTokenRequestDTO request = new RefreshTokenRequestDTO();
        request.setRefreshToken(refreshTokenValue);

        User user = new User();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);

        when(refreshTokenService.findByToken(refreshTokenValue)).thenReturn(refreshToken);
        doNothing().when(refreshTokenService).verifyExpiration(refreshToken);

        String generatedToken = "new-jwt-token";
        when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn(generatedToken);

        ResponseEntity<AuthenticationResponseDTO> response = authController.refreshToken(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        AuthenticationResponseDTO responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals(generatedToken, responseBody.getToken());
        assertEquals(refreshTokenValue, responseBody.getRefreshToken());

        verify(refreshTokenService).findByToken(refreshTokenValue);
        verify(refreshTokenService).verifyExpiration(refreshToken);
        verify(jwtService).generateToken(any(CustomUserDetails.class));
    }

    @Test
    void refreshToken_shouldThrowAppException_whenTokenNotFound() {
        String refreshTokenValue = "invalid-token";
        RefreshTokenRequestDTO request = new RefreshTokenRequestDTO();
        request.setRefreshToken(refreshTokenValue);

        when(refreshTokenService.findByToken(refreshTokenValue))
                .thenThrow(new AppException("Refresh token not found", HttpStatus.NOT_FOUND));

        AppException exception = assertThrows(AppException.class, () -> authController.refreshToken(request));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("Refresh token not found", exception.getMessage());
    }
}
