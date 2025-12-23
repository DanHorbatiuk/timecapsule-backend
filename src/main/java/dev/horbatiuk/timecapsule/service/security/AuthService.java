package dev.horbatiuk.timecapsule.service.security;

import dev.horbatiuk.timecapsule.exception.ConflictException;
import dev.horbatiuk.timecapsule.exception.NotFoundException;
import dev.horbatiuk.timecapsule.persistence.UserRepository;
import dev.horbatiuk.timecapsule.persistence.dto.security.AuthenticationRequestDTO;
import dev.horbatiuk.timecapsule.persistence.dto.security.AuthenticationResponseDTO;
import dev.horbatiuk.timecapsule.persistence.dto.security.RegisterRequestDTO;
import dev.horbatiuk.timecapsule.persistence.dto.user.UserDTO;
import dev.horbatiuk.timecapsule.persistence.entities.RefreshToken;
import dev.horbatiuk.timecapsule.persistence.entities.User;
import dev.horbatiuk.timecapsule.persistence.entities.VerificationToken;
import dev.horbatiuk.timecapsule.persistence.entities.enums.UserRole;
import dev.horbatiuk.timecapsule.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Validated
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final UserVerificationService userVerificationService;

    public AuthenticationResponseDTO register(RegisterRequestDTO dto) throws ConflictException {
        logger.info("Registering new user with email: {}", dto.getEmail());

        if (userRepository.findUserByEmail(dto.getEmail()).isPresent()) {
            logger.warn("Registration failed: email already exists - {}", dto.getEmail());
            throw new ConflictException("Email already exists");
        }

        User user = User.builder()
                .email(dto.getEmail())
                .name(dto.getName())
                .password(passwordEncoder.encode(dto.getPassword()))
                .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                .roles(Set.of(UserRole.ROLE_USER))
                .capsules(new ArrayList<>())
                .build();
        userRepository.save(user);
        logger.info("User registered successfully: {}", user.getEmail());

        VerificationToken verificationToken = VerificationToken.builder()
                .user(user)
                .token(UUID.randomUUID())
                .build();
        userVerificationService.addToken(verificationToken);
        logger.debug("Verification token created for user: {}", user.getEmail());

        return generateTokens(user);
    }

    public AuthenticationResponseDTO authenticate(AuthenticationRequestDTO request) throws NotFoundException {
        logger.info("Authenticating user: {}", request.getEmail());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findUserByEmail(request.getEmail())
                .orElseThrow(() -> {
                    logger.warn("Authentication failed: user not found - {}", request.getEmail());
                    return new NotFoundException("User not found");
                });

        logger.info("Authentication successful for user: {}", request.getEmail());
        return generateTokens(user);
    }


    private AuthenticationResponseDTO generateTokens(User user) {
        String jwt = jwtService.generateToken(new CustomUserDetails(user));
        RefreshToken refresh = refreshTokenService.createOrUpdateRefreshToken(user.getEmail());
        return AuthenticationResponseDTO.builder()
                .token(jwt)
                .refreshToken(refresh.getToken())
                .build();
    }

    public UserDTO getUserInfo(String email) throws NotFoundException {
        logger.debug("Fetching user info for: {}", email);
        User user = userRepository.findUserByEmail(email)
                .orElseThrow(() -> {
                    logger.warn("User info fetch failed: not found - {}", email);
                    return new NotFoundException("User not found");
                });
        logger.info("User info retrieved for: {}", email);
        return new UserDTO(user.getId(), user.getEmail(), user.getName());
    }
}
