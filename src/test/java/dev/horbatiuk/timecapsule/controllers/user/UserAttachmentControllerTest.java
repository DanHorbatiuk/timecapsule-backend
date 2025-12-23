package dev.horbatiuk.timecapsule.controllers.user;

import dev.horbatiuk.timecapsule.exception.controller.AppException;
import dev.horbatiuk.timecapsule.persistence.dto.attachment.AttachmentResponseDTO;
import dev.horbatiuk.timecapsule.security.CustomUserDetails;
import dev.horbatiuk.timecapsule.service.AttachmentService;
import dev.horbatiuk.timecapsule.service.CapsuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserAttachmentControllerTest {

    @InjectMocks
    private UserAttachmentController controller;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private CapsuleService capsuleService;

    @Mock
    private CustomUserDetails user;

    @Mock
    private MultipartFile file;

    private static final DataSize maxFileSize = DataSize.ofMegabytes(10);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(controller, "maxFileSize", maxFileSize);
    }

    @Test
    void getAttachments_accessDenied_throwsAppException() {
        UUID capsuleId = UUID.randomUUID();
        when(user.getEmail()).thenReturn("user@example.com");
        when(capsuleService.userHasAccess(capsuleId, user.getEmail())).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () ->
                controller.getAttachments(capsuleId, user));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("You do not have access to this capsule", ex.getMessage());
    }

    @Test
    void getAttachments_success() {
        UUID capsuleId = UUID.randomUUID();
        when(user.getEmail()).thenReturn("user@example.com");
        when(capsuleService.userHasAccess(capsuleId, user.getEmail())).thenReturn(true);

        List<AttachmentResponseDTO> attachments = List.of(new AttachmentResponseDTO(), new AttachmentResponseDTO());
        when(attachmentService.getAttachmentsByCapsuleId(capsuleId)).thenReturn(attachments);

        ResponseEntity<List<AttachmentResponseDTO>> response = controller.getAttachments(capsuleId, user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        verify(attachmentService).getAttachmentsByCapsuleId(capsuleId);
    }

    @Test
    void addAttachment_exceedsMaxAttachments_throwsAppException() {
        UUID capsuleId = UUID.randomUUID();

        when(user.isPremiumUser()).thenReturn(false);
        when(attachmentService.getAttachmentsByCapsuleId(capsuleId))
                .thenReturn(Collections.nCopies(5, new AttachmentResponseDTO())); // рівно ліміт

        AppException ex = assertThrows(AppException.class, () ->
                controller.addAttachment(capsuleId, user, "desc", file));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("Maximum number of attachments exceeded", ex.getMessage());
    }

    @Test
    void addAttachment_fileTooLarge_throwsAppException() {
        UUID capsuleId = UUID.randomUUID();

        when(user.isPremiumUser()).thenReturn(false);
        when(attachmentService.getAttachmentsByCapsuleId(capsuleId))
                .thenReturn(Collections.emptyList());
        when(file.getSize()).thenReturn(maxFileSize.toBytes() + 1);

        AppException ex = assertThrows(AppException.class, () ->
                controller.addAttachment(capsuleId, user, "desc", file));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.getStatus());
        assertEquals("File size exceeded", ex.getMessage());
    }

    @Test
    void addAttachment_success() throws Exception {
        UUID capsuleId = UUID.randomUUID();

        controller.premiumUserMaxAttachmentsPerCapsule = 5;
        controller.userMaxAttachmentsPerCapsule = 3;
        controller.maxFileSize = DataSize.ofMegabytes(1);

        when(user.isPremiumUser()).thenReturn(false);
        when(user.getEmail()).thenReturn("user@example.com");
        when(attachmentService.getAttachmentsByCapsuleId(capsuleId))
                .thenReturn(Collections.emptyList());

        when(file.getSize()).thenReturn(500L);

        ResponseEntity<Void> response = controller.addAttachment(capsuleId, user, "desc", file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(attachmentService).addAttachmentToCapsule(capsuleId, "desc", "user@example.com", file);
    }

    @Test
    void deleteAttachment_noAccess_throwsAppException() {
        UUID capsuleId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        when(user.getEmail()).thenReturn("user@example.com");
        when(capsuleService.userHasAccess(capsuleId, user.getEmail())).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () ->
                controller.deleteAttachment(capsuleId, attachmentId, user));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("You do not have access to this capsule", ex.getMessage());
    }

    @Test
    void deleteAttachment_success() throws Exception {
        UUID capsuleId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        when(user.getEmail()).thenReturn("user@example.com");
        when(capsuleService.userHasAccess(capsuleId, user.getEmail())).thenReturn(true);

        ResponseEntity<Void> response = controller.deleteAttachment(capsuleId, attachmentId, user);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(attachmentService).deleteAttachmentFromCapsule(capsuleId, attachmentId, "user@example.com");
    }

    @Test
    void deleteAttachment_illegalArgumentException_throwsNotFound() throws Exception {
        UUID capsuleId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        when(user.getEmail()).thenReturn("user@example.com");
        when(capsuleService.userHasAccess(capsuleId, user.getEmail())).thenReturn(true);

        doThrow(new IllegalArgumentException("Attachment not found"))
                .when(attachmentService).deleteAttachmentFromCapsule(capsuleId, attachmentId, "user@example.com");

        AppException ex = assertThrows(AppException.class, () ->
                controller.deleteAttachment(capsuleId, attachmentId, user));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("Attachment not found", ex.getMessage());
    }

    @Test
    void deleteAttachment_securityException_throwsForbidden() throws Exception {
        UUID capsuleId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        when(user.getEmail()).thenReturn("user@example.com");
        when(capsuleService.userHasAccess(capsuleId, user.getEmail())).thenReturn(true);

        doThrow(new SecurityException("Forbidden"))
                .when(attachmentService).deleteAttachmentFromCapsule(capsuleId, attachmentId, "user@example.com");

        AppException ex = assertThrows(AppException.class, () ->
                controller.deleteAttachment(capsuleId, attachmentId, user));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("Forbidden", ex.getMessage());
    }

    @Test
    void deleteAttachment_otherException_throwsInternalServerError() throws Exception {
        UUID capsuleId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        when(user.getEmail()).thenReturn("user@example.com");
        when(capsuleService.userHasAccess(capsuleId, user.getEmail())).thenReturn(true);

        doThrow(new RuntimeException("Unknown error"))
                .when(attachmentService).deleteAttachmentFromCapsule(capsuleId, attachmentId, "user@example.com");

        AppException ex = assertThrows(AppException.class, () ->
                controller.deleteAttachment(capsuleId, attachmentId, user));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
        assertEquals("Failed to delete attachment", ex.getMessage());
    }
}
