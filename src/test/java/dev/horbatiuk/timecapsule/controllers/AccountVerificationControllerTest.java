package dev.horbatiuk.timecapsule.controllers;

import dev.horbatiuk.timecapsule.exception.NotFoundException;
import dev.horbatiuk.timecapsule.exception.controller.AppException;
import dev.horbatiuk.timecapsule.persistence.entities.User;
import dev.horbatiuk.timecapsule.security.CustomUserDetails;
import dev.horbatiuk.timecapsule.service.UserService;
import dev.horbatiuk.timecapsule.service.security.UserVerificationService;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AccountVerificationControllerTest {

    @Mock
    private UserVerificationService userVerificationService;

    @Mock
    private UserService userService;

    @InjectMocks
    private AccountVerificationController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void sendVerificationEmail_shouldReturnOk_whenSuccess() throws Exception {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getEmail()).thenReturn("test@example.com");

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setVerified(false);

        when(userService.findUserByEmail("test@example.com")).thenReturn(user);
        doNothing().when(userVerificationService).sendVerificationEmail(user.getId(), user.getEmail(), user.isVerified());

        ResponseEntity<?> response = controller.sendVerificationEmail(userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userService).findUserByEmail("test@example.com");
        verify(userVerificationService).sendVerificationEmail(user.getId(), user.getEmail(), user.isVerified());
    }

    @Test
    void sendVerificationEmail_shouldThrowAppException_whenUserNotFound() throws NotFoundException {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getEmail()).thenReturn("missing@example.com");

        when(userService.findUserByEmail("missing@example.com"))
                .thenThrow(new NotFoundException("User not found"));

        AppException exception = assertThrows(AppException.class, () -> controller.sendVerificationEmail(userDetails));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("User not found", exception.getMessage());
    }

    @Test
    void sendVerificationEmail_shouldThrowAppException_whenMessagingFails() throws Exception {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getEmail()).thenReturn("fail@example.com");

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("fail@example.com");
        user.setVerified(false);

        when(userService.findUserByEmail("fail@example.com")).thenReturn(user);
        doThrow(new MessagingException("SMTP error")).when(userVerificationService)
                .sendVerificationEmail(user.getId(), user.getEmail(), user.isVerified());

        AppException exception = assertThrows(AppException.class, () -> controller.sendVerificationEmail(userDetails));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatus());
        assertEquals("Error sending verification email", exception.getMessage());
    }

    @Test
    void verifyAccount_shouldReturnOk_whenTokenValid() throws NotFoundException {
        UUID token = UUID.randomUUID();
        doNothing().when(userVerificationService).verifyToken(token);

        ResponseEntity<?> response = controller.verifyAccount(token);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userVerificationService).verifyToken(token);
    }

    @Test
    void verifyAccount_shouldThrowAppException_whenTokenInvalid() throws NotFoundException {
        UUID token = UUID.randomUUID();
        doThrow(new NotFoundException("Token not found")).when(userVerificationService).verifyToken(token);

        AppException exception = assertThrows(AppException.class, () -> controller.verifyAccount(token));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Token not found", exception.getMessage());
    }
}
