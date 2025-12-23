package dev.horbatiuk.timecapsule.security;

import dev.horbatiuk.timecapsule.persistence.entities.User;
import dev.horbatiuk.timecapsule.persistence.entities.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomUserDetailsTest {

    @Test
    void customUserDetails_methodsReturnExpectedValues() {
        User user = new User();
        user.setName("John Doe");
        user.setEmail("john@example.com");
        user.setPassword("password123");
        user.setVerified(true);
        user.setRoles(Set.of(UserRole.ROLE_ADMIN, UserRole.ROLE_PREMIUM));

        CustomUserDetails details = new CustomUserDetails(user);

        assertEquals("John Doe", details.getUsername());
        assertEquals("john@example.com", details.getEmail());
        assertEquals("password123", details.getPassword());
        assertTrue(details.isVerified());

        Collection<? extends GrantedAuthority> authorities = details.getAuthorities();
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_PREMIUM")));
        assertEquals(2, authorities.size());

        assertTrue(details.isAdmin());
        assertTrue(details.isPremiumUser());
    }

}
