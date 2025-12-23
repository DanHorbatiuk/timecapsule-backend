package dev.horbatiuk.timecapsule.controllers;

import dev.horbatiuk.timecapsule.exception.ConflictException;
import dev.horbatiuk.timecapsule.exception.NotFoundException;
import dev.horbatiuk.timecapsule.exception.controller.AppException;
import dev.horbatiuk.timecapsule.persistence.dto.security.AuthenticationRequestDTO;
import dev.horbatiuk.timecapsule.persistence.dto.security.AuthenticationResponseDTO;
import dev.horbatiuk.timecapsule.persistence.dto.security.RefreshTokenRequestDTO;
import dev.horbatiuk.timecapsule.persistence.dto.security.RegisterRequestDTO;
import dev.horbatiuk.timecapsule.persistence.entities.RefreshToken;
import dev.horbatiuk.timecapsule.persistence.entities.User;
import dev.horbatiuk.timecapsule.security.CustomUserDetails;
import dev.horbatiuk.timecapsule.service.security.AuthService;
import dev.horbatiuk.timecapsule.service.security.JwtService;
import dev.horbatiuk.timecapsule.service.security.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Endpoints for user registration, login and token refresh")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;

    @PostMapping("/register")
    @Operation(summary = "Register new user", description = "Creates a new user account using provided registration data.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User registered successfully",
                    content = @Content(schema = @Schema(implementation = AuthenticationResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "User already exists", content = @Content)
    })
    @CrossOrigin(
            origins = "http://localhost:5173",
            allowedHeaders = "*",
            methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE}
    )
    public ResponseEntity<AuthenticationResponseDTO> register(
            @Valid @RequestBody RegisterRequestDTO dto) {
        try {
            AuthenticationResponseDTO response = authService.register(dto);
            logger.info("User registered successfully with email: {}", dto.getEmail());
            return ResponseEntity.ok(response);
        } catch (ConflictException e) {
            logger.warn("Registration conflict - email already exists: {}", dto.getEmail(), e);
            throw new AppException("User already exists", HttpStatus.CONFLICT);
        }
    }

    @PostMapping("/authenticate")
    @Operation(summary = "Authenticate user", description = "Logs in a user using email and password.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentication successful",
                    content = @Content(schema = @Schema(implementation = AuthenticationResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<AuthenticationResponseDTO> authenticate(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Authentication request with email and password",
                    content = @Content(schema = @Schema(implementation = AuthenticationRequestDTO.class))
            )
            @Valid @RequestBody AuthenticationRequestDTO authRequest) {
        try {
            AuthenticationResponseDTO response = authService.authenticate(authRequest);
            logger.info("User authenticated successfully with email: {}", authRequest.getEmail());
            return ResponseEntity.ok(response);
        } catch (NotFoundException e) {
            logger.warn("Authentication failed - user not found: {}", authRequest.getEmail(), e);
            throw new AppException("User not found", HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh JWT token", description = "Generates a new JWT access token using a valid refresh token.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
                    content = @Content(schema = @Schema(implementation = AuthenticationResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Refresh token not found", content = @Content),
            @ApiResponse(responseCode = "403", description = "Refresh token expired", content = @Content)
    })
    public ResponseEntity<AuthenticationResponseDTO> refreshToken(
            @Valid @RequestBody RefreshTokenRequestDTO request
    ) {
        try {
            RefreshToken refreshToken = refreshTokenService.findByToken(request.getRefreshToken());

            refreshTokenService.verifyExpiration(refreshToken);

            User user = refreshToken.getUser();
            CustomUserDetails userDetails = new CustomUserDetails(user);
            String token = jwtService.generateToken(userDetails);

            logger.info("Refresh token used successfully for user: {}", user.getEmail());

            return ResponseEntity.ok(AuthenticationResponseDTO.builder()
                    .token(token)
                    .refreshToken(request.getRefreshToken())
                    .build());
        } catch (AppException e) {
            logger.warn("Refresh token error: {}", e.getMessage(), e);
            throw e;
        }
    }
}
