package dev.horbatiuk.timecapsule.service.security;

import dev.horbatiuk.timecapsule.security.CustomUserDetails;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTest {

    private JwtService jwtService;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();

        String secretKey = "YmFzZTY0c2VjcmV0YmFzZTY0c2VjcmV0YmFzZTY0c2VjcmV0YmFzZTY0c2VjcmV0";
        ReflectionTestUtils.setField(jwtService, "SECRET", secretKey);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 3600000L);

        userDetails = mock(CustomUserDetails.class);
        when(userDetails.getEmail()).thenReturn("test@example.com");
        when(userDetails.getAuthorities()).thenReturn(Collections.emptyList());
    }

    @Test
    void generateToken_ShouldReturnValidToken() {
        String token = jwtService.generateToken(userDetails);
        assertNotNull(token);
    }

    @Test
    void extractUsername_ShouldReturnCorrectUsername() {
        String token = jwtService.generateToken(userDetails);
        String username = jwtService.extractUsername(token);
        assertEquals("test@example.com", username);
    }

    @Test
    void validateToken_ShouldReturnTrue_WhenTokenIsValid() {
        String token = jwtService.generateToken(userDetails);
        boolean isValid = jwtService.validateToken(token, userDetails);
        assertTrue(isValid);
    }

    @Test
    void validateToken_ShouldReturnFalse_WhenUsernameIsWrong() {
        String token = jwtService.generateToken(userDetails);

        CustomUserDetails wrongUser = mock(CustomUserDetails.class);
        when(wrongUser.getEmail()).thenReturn("wrong@example.com");

        assertFalse(jwtService.validateToken(token, wrongUser));
    }

    @Test
    void extractExpiration_ShouldReturnFutureDate() {
        String token = jwtService.generateToken(userDetails);
        Date expiration = jwtService.extractExpiration(token);
        assertTrue(expiration.after(new Date()));
    }

    @Test
    void extractClaim_ShouldThrowException_OnInvalidToken() {
        String invalidToken = "invalid.token.value";

        JwtException exception = assertThrows(JwtException.class, () ->
                jwtService.extractUsername(invalidToken));
        assertEquals("Invalid JWT token", exception.getMessage());
    }
}
