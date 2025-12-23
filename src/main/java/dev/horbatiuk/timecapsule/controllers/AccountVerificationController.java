package dev.horbatiuk.timecapsule.controllers;

import dev.horbatiuk.timecapsule.exception.NotFoundException;
import dev.horbatiuk.timecapsule.exception.controller.AppException;
import dev.horbatiuk.timecapsule.persistence.UserRepository;
import dev.horbatiuk.timecapsule.persistence.dto.user.CheckUserVerificationResponse;
import dev.horbatiuk.timecapsule.persistence.dto.user.VerifyRequestDTO;
import dev.horbatiuk.timecapsule.persistence.entities.User;
import dev.horbatiuk.timecapsule.security.CustomUserDetails;
import dev.horbatiuk.timecapsule.service.UserService;
import dev.horbatiuk.timecapsule.service.security.UserVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/verify")
@RequiredArgsConstructor
@Tag(name = "Account Verification", description = "Endpoints for email verification of user accounts")
@CrossOrigin(
        origins = "http://localhost:5173",
        allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE}
)
public class AccountVerificationController {

    private static final Logger logger = LoggerFactory.getLogger(AccountVerificationController.class);

    private final UserVerificationService userVerificationService;
    private final UserService userService;

    @GetMapping("/send")
    @Operation(summary = "Send verification email", description = "Sends a verification email to the currently authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verification email sent successfully"),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error while sending email", content = @Content)
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> sendVerificationEmail(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        try {
            User user = userService.findUserByEmail(userDetails.getEmail());
            userVerificationService.sendVerificationEmail(user.getId(), user.getEmail(), user.isVerified());
            logger.info("Verification email sent to user: {}", user.getEmail());
            return ResponseEntity.ok().build();
        } catch (NotFoundException e) {
            logger.warn("User not found for verification email: {}", userDetails.getEmail(), e);
            throw new AppException("User not found", HttpStatus.NOT_FOUND);
        } catch (MessagingException e) {
            logger.error("Error sending verification email", e);
            throw new AppException("Error sending verification email", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            logger.error("Unexpected error during verification email sending", e);
            throw new AppException("Could not send verification email", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping
    @Operation(summary = "Verify user account", description = "Verifies the user account using the verification token received via email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account verified successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired verification token", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error during verification", content = @Content)
    })
    public ResponseEntity<?> verifyAccount(
            @Parameter(description = "UUID token received via email") @RequestParam UUID token
    ) {
        try {
            userVerificationService.verifyToken(token);
            logger.info("User account verified with token: {}", token);
            return ResponseEntity.ok().build();
        } catch (NotFoundException e) {
            logger.warn("Verification token not found or invalid: {}", token, e);
            throw new AppException(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            logger.error("Unexpected error during account verification with token: {}", token, e);
            throw new AppException("Verification failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/check")
    @Operation(summary = "Check user verification by email", description = "Return boolean variable, if verified then true else false")
    @ApiResponses( value = {
            @ApiResponse(responseCode = "200", description = "User verification status send successfully"),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error during verification check", content = @Content)
    })
    public ResponseEntity<CheckUserVerificationResponse> checkAccountVerification(
            @Parameter(description = "Email of account") @RequestParam String email
    ) {
        try {
            CheckUserVerificationResponse response = new CheckUserVerificationResponse();
            response.setUserVerified(userVerificationService.isUserByEmailVerified(email));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Unexpected error during account verification check", e);
            throw new AppException("Verification failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/verify/check")
    @Operation(summary = "Verify a user by token", description = "Verifies a user account using a verification token. Returns success status and message.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User verified successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(example = "{\"success\": true, \"message\": \"User verified successfully\"}"))),
            @ApiResponse(responseCode = "400", description = "Token is missing or invalid",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(example = "{\"success\": false, \"message\": \"Token is missing\"}"))),
            @ApiResponse(responseCode = "403", description = "Verification failed due to business rules",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(example = "{\"success\": false, \"message\": \"User is already verified\"}"))),
            @ApiResponse(responseCode = "500", description = "Server error during verification",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(example = "{\"success\": false, \"message\": \"Server error during verification\"}")))
    })
    public ResponseEntity<Map<String, Object>> verifyUser(@RequestBody VerifyRequestDTO body) {
        Map<String, Object> response = new HashMap<>();
        String token = body.getToken();
        if (token == null || token.isEmpty()) {
            response.put("success", false);
            response.put("message", "Token is missing");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            userVerificationService.verifyToken(UUID.fromString(token));
            response.put("success", true);
            response.put("message", "User verified successfully");
            response.put("redirectUrl", "http://localhost:5173/timecapsule/me");
            return ResponseEntity.ok(response);
        } catch (AppException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Server error during verification");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

}
