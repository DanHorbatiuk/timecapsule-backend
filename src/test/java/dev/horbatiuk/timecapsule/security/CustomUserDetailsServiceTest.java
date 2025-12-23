package dev.horbatiuk.timecapsule.security;

import dev.horbatiuk.timecapsule.persistence.UserRepository;
import dev.horbatiuk.timecapsule.persistence.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomUserDetailsServiceTest {

    private UserRepository userRepository;
    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new CustomUserDetailsService(userRepository);
    }

    @Test
    void loadUserByUsername_userExists_returnsCustomUserDetails() {
        String email = "user@example.com";
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPassword("password123");

        when(userRepository.findUserByEmail(email)).thenReturn(Optional.of(user));

        CustomUserDetails userDetails = service.loadUserByUsername(email);

        assertNotNull(userDetails);
        assertEquals(email, userDetails.getEmail());
        assertEquals(user.getPassword(), userDetails.getPassword());

        verify(userRepository, times(1)).findUserByEmail(email);
    }

    @Test
    void loadUserByUsername_userNotFound_throwsException() {
        String email = "missing@example.com";

        when(userRepository.findUserByEmail(email)).thenReturn(Optional.empty());

        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername(email));

        assertEquals("User not found with email: " + email, exception.getMessage());

        verify(userRepository, times(1)).findUserByEmail(email);
    }
}
