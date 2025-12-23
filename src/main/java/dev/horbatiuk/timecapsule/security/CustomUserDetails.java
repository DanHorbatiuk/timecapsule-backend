package dev.horbatiuk.timecapsule.security;

import dev.horbatiuk.timecapsule.persistence.entities.User;
import dev.horbatiuk.timecapsule.persistence.entities.enums.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {

    /*
        Administrator - ROLE_ADMIN,
        Premium User - ROLE_PREMIUM,
        Default User - ROLE_USER,
     */

    private final User user;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.name()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getUsername() {
        return user.getName();
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    public boolean isVerified() { return user.isVerified(); }

    public String getEmail() {
        return user.getEmail();
    }

    public boolean isAdmin() {
        return user.getRoles().contains(UserRole.ROLE_ADMIN);
    }

    public boolean isPremiumUser() {
        return user.getRoles().contains(UserRole.ROLE_PREMIUM);
    }

}
