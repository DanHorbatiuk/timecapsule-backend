package dev.horbatiuk.timecapsule.controllers.user;

import dev.horbatiuk.timecapsule.exception.NotFoundException;
import dev.horbatiuk.timecapsule.exception.aws.s3.S3ActionException;
import dev.horbatiuk.timecapsule.exception.controller.AppException;
import dev.horbatiuk.timecapsule.persistence.dto.attachment.AttachmentResponseDTO;
import dev.horbatiuk.timecapsule.security.CustomUserDetails;
import dev.horbatiuk.timecapsule.service.AttachmentService;
import dev.horbatiuk.timecapsule.service.CapsuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/user/capsule")
@RequiredArgsConstructor
@Tag(name = "User Attachments Controller", description = "Endpoints for user attachments management")
@SecurityRequirement(name = "bearerAuth")
@CrossOrigin(
        origins = "http://localhost:5173",
        allowedHeaders = "*",
        methods = {
                RequestMethod.GET,
                RequestMethod.POST,
                RequestMethod.PUT,
                RequestMethod.DELETE,
                RequestMethod.OPTIONS
        },
        allowCredentials = "true"
)
public class UserAttachmentController {

    private static final Logger logger = LoggerFactory.getLogger(UserAttachmentController.class);

    @Value("${app.user-max-attachments-per-capsule}")
    int userMaxAttachmentsPerCapsule;

    @Value("${app.premium-user-max-attachments-per-capsule}")
    int premiumUserMaxAttachmentsPerCapsule;

    @Value("${aws.s3.max-file-size}")
    DataSize maxFileSize;

    private final AttachmentService attachmentService;
    private final CapsuleService capsuleService;

    @GetMapping("/{capsuleId}/attachments")
    @Operation(summary = "Get attachments by capsule ID", description = "Returns a list of attachments for a specific capsule owned by the authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of attachments retrieved",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AttachmentResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Access denied to this capsule"),
    })
    public ResponseEntity<List<AttachmentResponseDTO>> getAttachments(
            @Parameter(description = "Capsule ID") @PathVariable UUID capsuleId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails
    ) {
        if (!capsuleService.userHasAccess(capsuleId, customUserDetails.getEmail())) {
            logger.warn("User {} tried to access attachments of capsule {} without permission",
                    customUserDetails.getEmail(), capsuleId);
            throw new AppException("You do not have access to this capsule", HttpStatus.FORBIDDEN);
        }
        List<AttachmentResponseDTO> attachments = attachmentService.getAttachmentsByCapsuleId(capsuleId);
        logger.info("User {} retrieved {} attachments for capsule {}", customUserDetails.getEmail(),
                attachments.size(), capsuleId);
        return ResponseEntity.ok(attachments);
    }

    @PostMapping(
            value = "/{capsuleId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(summary = "Add attachment to capsule", description = "Adds a new attachment (file + description) to a capsule")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Attachment added successfully"),
            @ApiResponse(responseCode = "403", description = "Attachment limit reached or access denied"),
            @ApiResponse(responseCode = "413", description = "File size exceeded limit"),
            @ApiResponse(responseCode = "500", description = "Error adding attachment")
    })
    public ResponseEntity<Void> addAttachment(
            @Parameter(description = "Capsule ID to add attachment to") @PathVariable UUID capsuleId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user,
            @Parameter(description = "Attachment description") @RequestParam String description,
            @Parameter(description = "File to upload") @RequestPart("file") MultipartFile file
    ) {
        int max = user.isPremiumUser() ? premiumUserMaxAttachmentsPerCapsule : userMaxAttachmentsPerCapsule;
        int current = attachmentService.getAttachmentsByCapsuleId(capsuleId).size();

        if (file.getSize() > maxFileSize.toBytes()) {
            logger.warn("User {} attempted to upload file exceeding max size ({} bytes) to capsule {}",
                    user.getEmail(), maxFileSize.toBytes(), capsuleId);
            throw new AppException("File size exceeded", HttpStatus.PAYLOAD_TOO_LARGE);
        }

        if (current >= max) {
            logger.warn("User {} exceeded max attachments per capsule ({}), capsule: {}",
                    user.getEmail(), max, capsuleId);
            throw new AppException("Maximum number of attachments exceeded", HttpStatus.FORBIDDEN);
        }

        try {
            attachmentService.addAttachmentToCapsule(capsuleId, description, user.getEmail(), file);
            logger.info("User {} added new attachment to capsule {}", user.getEmail(), capsuleId);
        } catch (AccessDeniedException e) {
            logger.warn("Access denied: {}", e.getMessage());
            throw new AppException(e.getMessage(), HttpStatus.FORBIDDEN);
        } catch (NotFoundException e) {
            logger.warn("Capsule not found: {}", capsuleId);
            throw new AppException(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (S3ActionException | IOException e) {
            logger.error("File storage error: {}", e.getMessage(), e);
            throw new AppException("Attachment could not be saved: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (PersistenceException e) {
            logger.error("Database error: {}", e.getMessage(), e);
            throw new AppException("Attachment saving failed", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage(), e);
            throw new AppException("Unexpected error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{capsuleId}/attachments/{attachmentId}")
    @Operation(summary = "Delete attachment", description = "Deletes an attachment from a capsule")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Attachment deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied to this capsule"),
            @ApiResponse(responseCode = "404", description = "Attachment not found"),
            @ApiResponse(responseCode = "500", description = "Error deleting attachment")
    })
    public ResponseEntity<Void> deleteAttachment(
            @Parameter(description = "Capsule ID") @PathVariable UUID capsuleId,
            @Parameter(description = "Attachment ID") @PathVariable UUID attachmentId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user
    ) {
        if (!capsuleService.userHasAccess(capsuleId, user.getEmail())) {
            logger.warn("User {} tried to delete attachment from capsule {} without permission",
                    user.getEmail(), capsuleId);
            throw new AppException("You do not have access to this capsule", HttpStatus.FORBIDDEN);
        }
        try {
            attachmentService.deleteAttachmentFromCapsule(capsuleId, attachmentId, user.getEmail());
            logger.info("User {} deleted attachment {} from capsule {}", user.getEmail(), attachmentId, capsuleId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            logger.warn("Attachment {} not found in capsule {} by user {}",
                    attachmentId, capsuleId, user.getEmail());
            throw new AppException(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (SecurityException e) {
            logger.warn("User {} forbidden to delete attachment {} from capsule {}",
                    user.getEmail(), attachmentId, capsuleId);
            throw new AppException(e.getMessage(), HttpStatus.FORBIDDEN);
        } catch (Exception e) {
            logger.error("Failed to delete attachment {} from capsule {} by user {}",
                    attachmentId, capsuleId, user.getEmail(), e);
            throw new AppException("Failed to delete attachment", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
