package dev.horbatiuk.timecapsule.controllers;

import dev.horbatiuk.timecapsule.exception.ConflictException;
import dev.horbatiuk.timecapsule.exception.NotFoundException;
import dev.horbatiuk.timecapsule.exception.controller.AppException;
import dev.horbatiuk.timecapsule.persistence.dto.user.UpdateUserInfoRequestDTO;
import dev.horbatiuk.timecapsule.persistence.dto.user.UserDTO;
import dev.horbatiuk.timecapsule.security.CustomUserDetails;
import dev.horbatiuk.timecapsule.service.UserService;
import dev.horbatiuk.timecapsule.service.security.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "Endpoints for managing the current user's profile")
@SecurityRequirement(name = "bearerAuth")
@CrossOrigin(
        origins = "http://localhost:5173",
        allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE}
)
public class ProfileController {

    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);

    private final UserService userService;
    private final AuthService authService;

    @GetMapping("/me")
    @Operation(summary = "Get current user profile", description = "Returns profile information for the authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User profile retrieved",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<UserDTO> getCurrentUser(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        String email = userDetails.getEmail();
        UserDTO userDto;
        try {
            userDto = authService.getUserInfo(email);
        } catch (NotFoundException e) {
            logger.error("User not found for email: {}", email, e);
            throw new AppException("User not found", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(userDto);
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user profile", description = "Updates the authenticated user's profile information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User profile updated successfully"),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "Email conflict", content = @Content)
    })
    public ResponseEntity<?> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateUserInfoRequestDTO dto) {
        try {
            userService.updateProfile(userDetails.getEmail(), dto);
        } catch (NotFoundException e) {
            logger.error("Update failed - user not found: {}", userDetails.getEmail(), e);
            throw new AppException(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (ConflictException e) {
            logger.error("Update conflict for user: {}", userDetails.getEmail(), e);
            throw new AppException(e.getMessage(), HttpStatus.CONFLICT);
        }
        return ResponseEntity.ok().build();
    }
}
