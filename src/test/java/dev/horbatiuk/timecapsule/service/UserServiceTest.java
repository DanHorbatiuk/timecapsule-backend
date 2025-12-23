package dev.horbatiuk.timecapsule.service;

import dev.horbatiuk.timecapsule.exception.ConflictException;
import dev.horbatiuk.timecapsule.exception.NotFoundException;
import dev.horbatiuk.timecapsule.persistence.UserRepository;
import dev.horbatiuk.timecapsule.persistence.dto.user.UpdateUserInfoRequestDTO;
import dev.horbatiuk.timecapsule.persistence.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private final String testEmail = "test@example.com";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void findUserByEmail_UserExists_ReturnsUser() throws NotFoundException {
        User user = new User();
        user.setEmail(testEmail);

        when(userRepository.findUserByEmail(testEmail)).thenReturn(Optional.of(user));

        User foundUser = userService.findUserByEmail(testEmail);

        assertNotNull(foundUser);
        assertEquals(testEmail, foundUser.getEmail());
        verify(userRepository, times(1)).findUserByEmail(testEmail);
    }

    @Test
    void findUserByEmail_UserNotFound_ThrowsNotFoundException() {
        when(userRepository.findUserByEmail(testEmail)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> userService.findUserByEmail(testEmail));

        assertEquals("User not found", exception.getMessage());
        verify(userRepository, times(1)).findUserByEmail(testEmail);
    }

    @Test
    void updateProfile_UpdateNameOnly_SavesUpdatedUser() throws NotFoundException, ConflictException {
        User user = new User();
        user.setEmail(testEmail);
        user.setName("Old Name");
        user.setPassword("encoded-password");

        UpdateUserInfoRequestDTO dto = new UpdateUserInfoRequestDTO();
        dto.setName("New Name");
        dto.setNewPassword(null);

        when(userRepository.findUserByEmail(testEmail)).thenReturn(Optional.of(user));

        userService.updateProfile(testEmail, dto);

        assertEquals("New Name", user.getName());
        verify(userRepository).save(user);
    }

    @Test
    void updateProfile_UpdatePassword_Success() throws NotFoundException, ConflictException {
        User user = new User();
        user.setEmail(testEmail);
        user.setPassword("encoded-old-password");

        UpdateUserInfoRequestDTO dto = new UpdateUserInfoRequestDTO();
        dto.setOldPassword("old-password");
        dto.setNewPassword("new-password");

        when(userRepository.findUserByEmail(testEmail)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-password", "encoded-old-password")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new-password");

        userService.updateProfile(testEmail, dto);

        assertEquals("encoded-new-password", user.getPassword());
        verify(userRepository).save(user);
    }

    @Test
    void updateProfile_UpdatePassword_OldPasswordMismatch_ThrowsConflictException() {
        User user = new User();
        user.setEmail(testEmail);
        user.setPassword("encoded-old-password");

        UpdateUserInfoRequestDTO dto = new UpdateUserInfoRequestDTO();
        dto.setOldPassword("wrong-old-password");
        dto.setNewPassword("new-password");

        when(userRepository.findUserByEmail(testEmail)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-old-password", "encoded-old-password")).thenReturn(false);

        ConflictException exception = assertThrows(ConflictException.class,
                () -> userService.updateProfile(testEmail, dto));

        assertEquals("Your old password does not match", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_UserNotFound_ThrowsNotFoundException() {
        UpdateUserInfoRequestDTO dto = new UpdateUserInfoRequestDTO();
        dto.setName("New Name");

        when(userRepository.findUserByEmail(testEmail)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> userService.updateProfile(testEmail, dto));

        assertEquals("User not found", exception.getMessage());
        verify(userRepository, never()).save(any());
    }
}
