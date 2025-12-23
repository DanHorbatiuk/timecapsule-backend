package dev.horbatiuk.timecapsule.controllers.admin;

import dev.horbatiuk.timecapsule.exception.NotFoundException;
import dev.horbatiuk.timecapsule.exception.controller.AppException;
import dev.horbatiuk.timecapsule.persistence.CapsuleRepository;
import dev.horbatiuk.timecapsule.persistence.dto.attachment.AttachmentResponseDTO;
import dev.horbatiuk.timecapsule.persistence.dto.capsule.CapsuleResponseDTO;
import dev.horbatiuk.timecapsule.persistence.dto.capsule.EditCapsuleDTO;
import dev.horbatiuk.timecapsule.persistence.entities.Capsule;
import dev.horbatiuk.timecapsule.persistence.entities.enums.CapsuleStatus;
import dev.horbatiuk.timecapsule.service.AttachmentService;
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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.scheduler.model.GetScheduleResponse;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/capsules")
@RequiredArgsConstructor
@Tag(name = "Administrator Capsule Controller", description = "Endpoints for administrator capsule management")
@SecurityRequirement(name = "bearerAuth")
@CrossOrigin(origins = "http://localhost:5173", allowedHeaders = "*")
public class AdminCapsuleController {

    private static final Logger logger = LoggerFactory.getLogger(AdminCapsuleController.class);

    private final CapsuleService capsuleService;
    private final CapsuleRepository capsuleRepository;
    private final S3Service s3Service;
    private final EventBridgeScheduledService eventBridgeScheduledService;
    private final AttachmentService attachmentService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    @Operation(summary = "List capsules (admin) with filters and pagination")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Filtered list returned",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CapsuleResponseDTO.class))),
    })
    public ResponseEntity<Page<CapsuleResponseDTO>> listCapsules(
            @Parameter(description = "Filter by user email") @RequestParam(required = false) String email,
            @Parameter(description = "Filter by userId (UUID)") @RequestParam(required = false) UUID userId,
            @Parameter(description = "Filter by capsule status") @RequestParam(required = false) CapsuleStatus status,
            @Parameter(description = "Filter by title (contains)") @RequestParam(required = false) String title,
            @Parameter(description = "OpenAt from (ISO-8601)") @RequestParam(required = false) String openFrom,
            @Parameter(description = "OpenAt to (ISO-8601)") @RequestParam(required = false) String openTo,
            @Parameter(description = "Page index (0..N)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort, e.g. createdAt,desc") @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        logger.info("Admin listing capsules with filters email={}, userId={}, status={}, title={}", email, userId, status, title);

        String[] sortParts = sort.split(",");
        String sortBy = sortParts[0];
        Sort.Direction dir = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1]) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(dir, sortBy));

        OffsetDateTime from = null, to = null;
        try {
            if (openFrom != null && !openFrom.isBlank()) from = OffsetDateTime.parse(openFrom);
            if (openTo != null && !openTo.isBlank()) to = OffsetDateTime.parse(openTo);
        } catch (DateTimeParseException ex) {
            logger.warn("Invalid date range provided: openFrom={}, openTo={}", openFrom, openTo);
            return ResponseEntity.badRequest().build();
        }

        Page<CapsuleResponseDTO> result = capsuleService.findCapsulesByFilters(
                email, userId, status, title, from, to, pageRequest
        );
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{capsuleId}")
    @Operation(summary = "Get single capsule by ID (admin)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Capsule returned",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CapsuleResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Capsule not found")
    })
    public ResponseEntity<CapsuleResponseDTO> getCapsuleById(@PathVariable UUID capsuleId) {
        try {
            CapsuleResponseDTO dto = capsuleService.findCapsuleById(capsuleId);
            return ResponseEntity.ok(dto);
        } catch (NotFoundException e) {
            logger.warn("Capsule {} not found", capsuleId);
            throw new AppException("Capsule not found", HttpStatus.NOT_FOUND);
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{capsuleId}/attachments")
    @Operation(summary = "Get attachments for capsule (admin)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of attachments returned",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AttachmentResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Capsule not found")
    })
    public ResponseEntity<List<AttachmentResponseDTO>> getAttachments(@PathVariable UUID capsuleId) {
        try {
            // ensure capsule exists
            capsuleService.findCapsuleById(capsuleId);
            List<AttachmentResponseDTO> attachments = attachmentService.getAttachmentsByCapsuleId(capsuleId);
            return ResponseEntity.ok(attachments);
        } catch (NotFoundException e) {
            logger.warn("Capsule {} not found when fetching attachments", capsuleId);
            throw new AppException("Capsule not found", HttpStatus.NOT_FOUND);
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get capsules by userId (UUID)")
    public ResponseEntity<List<CapsuleResponseDTO>> getByUserId(@PathVariable UUID userId) {
        logger.info("Admin requested capsules for userId {}", userId);
        List<CapsuleResponseDTO> capsules = capsuleService.findCapsulesByUserId(userId);
        return ResponseEntity.ok(capsules);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/by-email")
    @Operation(summary = "Get capsules by user email")
    public ResponseEntity<List<CapsuleResponseDTO>> getByEmail(@RequestParam String email) {
        logger.info("Admin requested capsules for email {}", email);
        List<CapsuleResponseDTO> capsules = capsuleService.findCapsulesByUserEmail(email);
        return ResponseEntity.ok(capsules);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{capsuleId}/metadata")
    @Operation(summary = "Update capsule metadata (admin)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Updated successfully"),
            @ApiResponse(responseCode = "404", description = "Capsule not found")
    })
    public ResponseEntity<Void> updateMetadata(
            @PathVariable UUID capsuleId,
            @RequestBody EditCapsuleDTO dto
    ) {
        try {
            capsuleService.editCapsuleAsAdmin(capsuleId, dto); /* TODO: implement in service or reuse existing edit method with admin flag */
            return ResponseEntity.ok().build();
        } catch (NotFoundException e) {
            throw new AppException("Capsule not found", HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            logger.error("Error updating metadata for capsule {}", capsuleId, e);
            throw new AppException("Failed to update capsule metadata", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{capsuleId}/status")
    @Operation(summary = "Set capsule status (admin)")
    public ResponseEntity<Void> setStatus(
            @PathVariable UUID capsuleId,
            @RequestParam CapsuleStatus status
    ) {
        logger.info("Admin setting status for {} -> {}", capsuleId, status);
        try {
            CapsuleResponseDTO dto = capsuleService.findCapsuleById(capsuleId);
            capsuleService.setCapsuleStatus(capsuleId, status);

            GetScheduleResponse scheduleResponse = null;
            try {
                scheduleResponse = eventBridgeScheduledService.getSchedule(capsuleId);
            } catch (NotFoundException e) {
                logger.info("Schedule not found for capsule {}, will create new one", capsuleId);
            }

            if (status == CapsuleStatus.ACTIVE) {
                try {
                    eventBridgeScheduledService.updateSchedule(scheduleResponse, true, capsuleId);
                } catch (NotFoundException e) {
                    eventBridgeScheduledService.createNewSchedule(capsuleId, dto.getOpenAt().toInstant());
                }
                s3Service.uploadCapsuleData(dto);
            } else {
                try {
                    eventBridgeScheduledService.updateSchedule(scheduleResponse, true, capsuleId);
                } catch (NotFoundException e) {
                    logger.info("No schedule to disable for capsule {}", capsuleId);
                }
            }
            return ResponseEntity.ok().build();
        } catch (NotFoundException e) {
            throw new AppException("Capsule not found", HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            logger.error("Failed to change status for capsule {}", capsuleId, e);
            throw new AppException("Failed to change capsule status", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{capsuleId}/resync")
    @Operation(summary = "Force re-upload to S3 and recreate schedule")
    public ResponseEntity<Void> resyncCapsule(@PathVariable UUID capsuleId) {
        logger.info("Admin resync requested for capsule {}", capsuleId);
        try {
            CapsuleResponseDTO dto = capsuleService.findCapsuleById(capsuleId);
            // re-upload to s3
            s3Service.uploadCapsuleData(dto);
            // recreate schedule (delete/create)
            try {
                eventBridgeScheduledService.deleteSchedule(capsuleId);
            } catch (NotFoundException ignored) {}
            eventBridgeScheduledService.createNewSchedule(capsuleId, dto.getOpenAt().toInstant());
            return ResponseEntity.ok().build();
        } catch (NotFoundException e) {
            throw new AppException("Capsule not found", HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            logger.error("Failed to resync capsule {}", capsuleId, e);
            throw new AppException("Failed to resync capsule", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{capsuleId}")
    @Operation(summary = "Delete capsule by ID (admin)")
    public ResponseEntity<Void> deleteCapsule(@PathVariable UUID capsuleId) {
        logger.info("Admin requested deletion of capsule {}", capsuleId);
        try {
            try {
                eventBridgeScheduledService.deleteSchedule(capsuleId);
            } catch (NotFoundException ignored) {}
            attachmentService.deleteAllAttachmentsFromCapsule(capsuleId);
            Capsule c = capsuleService.findCapsuleEntityById(capsuleId);
            capsuleRepository.delete(c);
            return ResponseEntity.noContent().build();
        } catch (NotFoundException e) {
            throw new AppException("Capsule not found", HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            logger.error("Error deleting capsule {}", capsuleId, e);
            throw new AppException("Error deleting capsule", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/by-email")
    @Operation(summary = "Delete all capsules for given email (admin) — USE WITH CAUTION")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "400", description = "Email missing"),
            @ApiResponse(responseCode = "500", description = "Error")
    })
    public ResponseEntity<Void> deleteByEmail(@RequestParam String email) {
        if (email == null || email.isBlank()) return ResponseEntity.badRequest().build();
        logger.warn("Admin initiating bulk delete for email {}", email);
        try {
            // TODO: implement efficient service method to delete all capsules for email (handles s3/schedules/attachments)
            capsuleService.deleteAllCapsulesByEmail(email);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed bulk delete for email {}", email, e);
            throw new AppException("Failed bulk delete", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
