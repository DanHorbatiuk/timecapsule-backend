package dev.horbatiuk.timecapsule.controllers.user;

import dev.horbatiuk.timecapsule.exception.ErrorResponse;
import dev.horbatiuk.timecapsule.exception.NotFoundException;
import dev.horbatiuk.timecapsule.exception.aws.InternalAwsException;
import dev.horbatiuk.timecapsule.exception.controller.AppException;
import dev.horbatiuk.timecapsule.persistence.CapsuleRepository;
import dev.horbatiuk.timecapsule.persistence.dto.capsule.CapsuleCreateDTO;
import dev.horbatiuk.timecapsule.persistence.dto.capsule.CapsuleResponseDTO;
import dev.horbatiuk.timecapsule.persistence.dto.capsule.EditCapsuleDTO;
import dev.horbatiuk.timecapsule.persistence.entities.enums.CapsuleStatus;
import dev.horbatiuk.timecapsule.security.CustomUserDetails;
import dev.horbatiuk.timecapsule.service.CapsuleService;
import dev.horbatiuk.timecapsule.service.aws.EventBridgeScheduledService;
import dev.horbatiuk.timecapsule.service.aws.S3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.scheduler.model.GetScheduleResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Tag(name = "User Capsule Controller", description = "Endpoints for users to manage their own capsules")
@SecurityRequirement(name = "bearerAuth")
@CrossOrigin(
        origins = "http://localhost:5173",
        allowedHeaders = "*",
        methods = {
                RequestMethod.GET,
                RequestMethod.POST,
                RequestMethod.PUT,
                RequestMethod.DELETE,
                RequestMethod.PATCH,
                RequestMethod.OPTIONS
        },
        allowCredentials = "true"
)
public class UserCapsuleController {

    private static final Logger logger = LoggerFactory.getLogger(UserCapsuleController.class);

    @Value("${app.user-max-capsules}")
    private int userMaxCapsules;

    @Value("${app.premium-user-max-capsules}")
    private int premiumUserMaxCapsules;

    private final CapsuleRepository capsuleRepository;
    private final S3Service s3Service;
    private final CapsuleService capsuleService;
    private final EventBridgeScheduledService eventBridgeScheduledService;

    @GetMapping("/capsules")
    @Operation(summary = "Get user's capsules", description = "Returns a list of capsules owned by the currently authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of user's capsules returned",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CapsuleResponseDTO.class)))
    })
    public ResponseEntity<List<CapsuleResponseDTO>> getCapsulesByUser(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user
    ) {
        List<CapsuleResponseDTO> capsuleResponseDTOList =
                capsuleService.findCapsulesByEmail(user.getEmail());
        if (capsuleResponseDTOList.isEmpty()) {
            logger.info("No capsules found for user {}", user.getEmail());
            return ResponseEntity.ok(new ArrayList<>());
        }
        logger.info("Returning {} capsules for user {}", capsuleResponseDTOList.size(), user.getEmail());
        return ResponseEntity.ok(capsuleResponseDTOList);
    }

    @PostMapping("/capsules")
    @Operation(
            summary = "Create new capsule",
            description = "Creates a new capsule for the authenticated and verified user and returns the created capsule"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Capsule created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CapsuleResponseDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "User is not verified or capsule limit reached",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User is not authenticated",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<CapsuleResponseDTO> addNewCapsuleByUser(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CapsuleCreateDTO dto
    ) {
        if (!userDetails.isVerified()) {
            throw new AppException("User is not verified", HttpStatus.FORBIDDEN);
        }
        int maxCapsules = userDetails.isPremiumUser()
                ? premiumUserMaxCapsules
                : userMaxCapsules;
        long capsuleCount = capsuleRepository.countByAppUserEmail(userDetails.getEmail());
        if (capsuleCount >= maxCapsules) {
            throw new AppException(
                    "Limit reached, max: " + maxCapsules + " capsules",
                    HttpStatus.FORBIDDEN
            );
        }
        CapsuleResponseDTO created =
                capsuleService.addNewCapsule(userDetails.getEmail(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/capsules/{capsuleId}")
    @Operation(summary = "Activate capsule", description = "Marks a capsule as active and schedules its delivery using AWS EventBridge")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Capsule activated successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied to this capsule"),
            @ApiResponse(responseCode = "500", description = "Error activating capsule")
    })
    public ResponseEntity<Void> changeCapsuleStatusToActive(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID capsuleId
    ) {
        if (!capsuleService.userHasAccess(capsuleId, userDetails.getEmail())) {
            logger.warn("User {} tried to access capsule {} without permission", userDetails.getEmail(), capsuleId);
            throw new AppException("You do not have access to this capsule", HttpStatus.FORBIDDEN);
        }

        try {
            GetScheduleResponse scheduleResponse = null;
            try {
                scheduleResponse = eventBridgeScheduledService.getSchedule(capsuleId);
            } catch (NotFoundException e) {
                logger.info("Schedule not found for capsule {}, will create new one", capsuleId);
            }
            capsuleService.setCapsuleStatus(capsuleId, CapsuleStatus.ACTIVE);
            CapsuleResponseDTO capsuleDTO = capsuleService.findCapsuleById(capsuleId);
            s3Service.uploadCapsuleData(capsuleDTO);
            if (scheduleResponse != null) {
                eventBridgeScheduledService.updateSchedule(scheduleResponse, true, capsuleId);
            } else {
                eventBridgeScheduledService.createNewSchedule(capsuleId, capsuleDTO.getOpenAt().toInstant());
            }
            logger.info("Capsule {} activated by user {}", capsuleId, userDetails.getEmail());
            return ResponseEntity.ok().build();
        } catch (InternalAwsException e) {
            logger.error("Internal AWS Scheduler error activating capsule {}", capsuleId, e);
            throw new AppException("Failed to activate capsule: " + capsuleId, HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            logger.error("Failed to activate capsule {} for user {}", capsuleId, userDetails.getEmail(), e);
            throw new AppException("Failed to activate capsule: " + capsuleId, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/capsules/{capsuleId}")
    @Operation(summary = "Edit capsule", description = "Allows the user to edit capsule metadata before activation")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Capsule edited successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied to this capsule"),
            @ApiResponse(responseCode = "404", description = "Capsule not found")
    })
    public ResponseEntity<Void> editCapsule(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "ID of the capsule to edit") @PathVariable UUID capsuleId,
            @Valid @RequestBody EditCapsuleDTO editCapsuleDTO
    ) {
        if (!capsuleService.userHasAccess(capsuleId, userDetails.getEmail())) {
            logger.warn("User {} tried to edit capsule {} without permission", userDetails.getEmail(), capsuleId);
            throw new AppException("You do not have access to this capsule", HttpStatus.FORBIDDEN);
        }
        try {
            CapsuleResponseDTO capsuleResponseDTO = capsuleService.findCapsuleById(capsuleId);
            if (capsuleResponseDTO.getStatus().equals(CapsuleStatus.ACTIVE)) {
                logger.warn("User {} tried to edit capsule with ACTIVE status", userDetails.getEmail());
                throw new AppException("You can't change activated capsule data", HttpStatus.FORBIDDEN);
            }
            capsuleService.editCapsule(capsuleId, editCapsuleDTO);
            logger.info("Capsule {} edited by user {}", capsuleId, userDetails.getEmail());
        } catch (NotFoundException e) {
            logger.warn("Capsule {} not found while editing by user {}", capsuleId, userDetails.getEmail());
            throw new AppException("Capsule not found", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok().build();
    }
}
