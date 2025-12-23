package dev.horbatiuk.timecapsule.service.security;

import dev.horbatiuk.timecapsule.exception.NotFoundException;
import dev.horbatiuk.timecapsule.exception.controller.AppException;
import dev.horbatiuk.timecapsule.persistence.VerificationTokenRepository;
import dev.horbatiuk.timecapsule.persistence.entities.User;
import dev.horbatiuk.timecapsule.persistence.entities.VerificationToken;
import dev.horbatiuk.timecapsule.service.EmailSenderService;
import dev.horbatiuk.timecapsule.service.UserService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(UserVerificationService.class);

    private final EmailSenderService emailSenderService;
    private final UserService userService;
    private final VerificationTokenRepository verificationTokenRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    @Async
    public void sendVerificationEmail(UUID userId, String email, boolean verified) throws NotFoundException, MessagingException {
        if (verified) {
            logger.info("User with email {} is already verified. Skipping email sending.", email);
            return;
        }
        User user = userService.findUserByEmail(email);
        VerificationToken verificationToken = verificationTokenRepository
                .findVerificationTokenByUser(user)
                .orElseThrow(() -> {
                    logger.warn("Verification token not found for user ID: {}", userId);
                    return new NotFoundException("Verification token not found for user: " + userId);
                });
        String frontendBaseUrl = "http://localhost:5173";
        String verificationLink = UriComponentsBuilder.fromHttpUrl(frontendBaseUrl)
                .path("/verify")
                .queryParam("token", verificationToken.getToken())
                .toUriString();

        try {
            logger.info("Sending verification email to user: {}", email);
            emailSenderService.sendVerificationEmail(email, verificationLink);
            logger.info("Verification email sent to: {}", email);
        } catch (MessagingException e) {
            logger.error("Failed to send verification email to {}: {}", email, e.getMessage());
            throw new MessagingException(e.getMessage());
        }
    }


    @Async
    public boolean isUserByEmailVerified(String email) throws NotFoundException {
        User user = userService.findUserByEmail(email);
        return user.isVerified();
    }

    @Transactional
    public void addToken(VerificationToken token) {
        try {
            verificationTokenRepository.save(token);
            logger.info("Verification token saved for user: {}", token.getUser().getEmail());
        } catch (DataAccessException e) {
            logger.error("Failed to save verification token for user {}: {}", token.getUser().getEmail(), e.getMessage());
            throw new AppException("Failed to save verification token", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public void verifyToken(UUID token) throws NotFoundException {
        logger.info("Verifying token: {}", token);
        VerificationToken verificationToken = verificationTokenRepository.findVerificationTokenByToken(token)
                .orElseThrow(() -> {
                    logger.warn("Verification token not found: {}", token);
                    return new NotFoundException("Verification token not found: " + token);
                });
        updateUserStatus(verificationToken.getUser().getEmail());
        try {
            verificationTokenRepository.delete(verificationToken);
            logger.info("Verification token deleted after verification for user: {}", verificationToken.getUser().getEmail());
        } catch (DataAccessException e) {
            logger.error("Failed to delete verification token for user: {}", verificationToken.getUser().getEmail());
            throw new NotFoundException("Failed to save verification token");
        }
    }

    @Transactional
    protected void updateUserStatus(String email) throws NotFoundException {
        logger.debug("Updating verification status for user: {}", email);
        User user = userService.findUserByEmail(email);
        if (user.isVerified()) {
            logger.info("User {} is already verified. No update needed.", email);
            return;
        }
        user.setVerified(true);
        logger.info("User {} marked as verified.", email);
    }
}
