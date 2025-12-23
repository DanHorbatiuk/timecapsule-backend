package dev.horbatiuk.timecapsule.controllers;

import dev.horbatiuk.timecapsule.exception.ConflictException;
import dev.horbatiuk.timecapsule.exception.NotFoundException;
import dev.horbatiuk.timecapsule.exception.controller.AppException;
import dev.horbatiuk.timecapsule.persistence.dto.user.UpdateUserInfoRequestDTO;
import dev.horbatiuk.timecapsule.persistence.dto.user.UserDTO;
import dev.horbatiuk.timecapsule.security.CustomUserDetails;
import dev.horbatiuk.timecapsule.service.UserService;
import dev.horbatiuk.timecapsule.service.security.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ProfileControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private AuthService authService;

    @InjectMocks
    private ProfileController profileController;

    @Mock
    private CustomUserDetails customUserDetails;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getCurrentUser_shouldReturnUserDTO_whenUserExists() throws NotFoundException {
        String email = "test@example.com";
        UserDTO userDTO = new UserDTO();
        userDTO.setEmail(email);

        when(customUserDetails.getEmail()).thenReturn(email);
        when(authService.getUserInfo(email)).thenReturn(userDTO);

        ResponseEntity<UserDTO> response = profileController.getCurrentUser(customUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(userDTO, response.getBody());

        verify(authService).getUserInfo(email);
    }

    @Test
    void getCurrentUser_shouldThrowAppException_whenUserNotFound() throws NotFoundException {
        String email = "test@example.com";

        when(customUserDetails.getEmail()).thenReturn(email);
        when(authService.getUserInfo(email)).thenThrow(new NotFoundException("User not found"));

        AppException ex = assertThrows(AppException.class, () -> profileController.getCurrentUser(customUserDetails));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void updateProfile_shouldReturnOk_whenUpdateSuccessful() throws ConflictException, NotFoundException {
        String email = "test@example.com";
        UpdateUserInfoRequestDTO dto = new UpdateUserInfoRequestDTO();

        when(customUserDetails.getEmail()).thenReturn(email);

        ResponseEntity<?> response = profileController.updateProfile(customUserDetails, dto);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userService).updateProfile(email, dto);
    }

    @Test
    void updateProfile_shouldThrowAppExceptionNotFound_whenUserNotFound() throws ConflictException, NotFoundException {
        String email = "test@example.com";
        UpdateUserInfoRequestDTO dto = new UpdateUserInfoRequestDTO();

        when(customUserDetails.getEmail()).thenReturn(email);
        doThrow(new NotFoundException("User not found")).when(userService).updateProfile(email, dto);

        AppException ex = assertThrows(AppException.class,
                () -> profileController.updateProfile(customUserDetails, dto));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void updateProfile_shouldThrowAppExceptionConflict_whenConflictOccurs() throws ConflictException, NotFoundException {
        String email = "test@example.com";
        UpdateUserInfoRequestDTO dto = new UpdateUserInfoRequestDTO();

        when(customUserDetails.getEmail()).thenReturn(email);
        doThrow(new ConflictException("Conflict error")).when(userService).updateProfile(email, dto);

        AppException ex = assertThrows(AppException.class,
                () -> profileController.updateProfile(customUserDetails, dto));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Conflict error", ex.getMessage());
    }
}
