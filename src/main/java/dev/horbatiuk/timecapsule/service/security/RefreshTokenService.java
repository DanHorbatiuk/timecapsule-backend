package dev.horbatiuk.timecapsule.service.security;

import dev.horbatiuk.timecapsule.exception.controller.AppException;
import dev.horbatiuk.timecapsule.persistence.RefreshTokenRepository;
import dev.horbatiuk.timecapsule.persistence.UserRepository;
import dev.horbatiuk.timecapsule.persistence.entities.RefreshToken;
import dev.horbatiuk.timecapsule.persistence.entities.User;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public RefreshToken createOrUpdateRefreshToken(String email) {
        logger.info("Creating or updating refresh token for user: {}", email);

        User user = userRepository.findUserByEmail(email)
                .orElseThrow(() -> {
                    logger.warn("User not found while generating refresh token: {}", email);
                    return new AppException("User not found", HttpStatus.NOT_FOUND);
                });

        Optional<RefreshToken> existingTokenOpt = refreshTokenRepository.findByUser(user);
        RefreshToken refreshToken;

        if (existingTokenOpt.isPresent()) {
            refreshToken = existingTokenOpt.get();
            refreshToken.setToken(UUID.randomUUID().toString());
            refreshToken.setExpiryDate(Instant.now().plus(7, ChronoUnit.DAYS));
            logger.debug("Existing refresh token updated for user: {}", email);
        } else {
            refreshToken = RefreshToken.builder()
                    .user(user)
                    .token(UUID.randomUUID().toString())
                    .expiryDate(Instant.now().plus(7, ChronoUnit.DAYS))
                    .build();
            logger.debug("New refresh token created for user: {}", email);
        }

        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public void verifyExpiration(RefreshToken token) {
        logger.debug("Verifying refresh token expiration for token: {}", token.getToken());
        if (token.getExpiryDate().isBefore(Instant.now())) {
            logger.warn("Refresh token expired: {}", token.getToken());
            refreshTokenRepository.delete(token);
            throw new AppException("Refresh token expired. Please login again.", HttpStatus.UNAUTHORIZED);
        }
        logger.debug("Refresh token is valid: {}", token.getToken());
    }

    @Transactional
    public void deleteByUser(String email) {
        logger.info("Deleting refresh token for user: {}", email);
        User user = userRepository.findUserByEmail(email)
                .orElseThrow(() -> {
                    logger.warn("User not found during token deletion: {}", email);
                    return new AppException("User not found", HttpStatus.NOT_FOUND);
                });
        refreshTokenRepository.deleteByUser(user);
        logger.info("Refresh token deleted for user: {}", email);
    }

    public RefreshToken findByToken(@NotBlank(message = "Refresh token is required") String refreshToken) {
        logger.debug("Searching for refresh token: {}", refreshToken);
        return refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> {
                    logger.warn("Refresh token not found: {}", refreshToken);
                    return new AppException("Refresh token not found.", HttpStatus.NOT_FOUND);
                });
    }
}
