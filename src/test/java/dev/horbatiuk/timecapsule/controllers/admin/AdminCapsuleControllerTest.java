package dev.horbatiuk.timecapsule.controllers.admin;

import dev.horbatiuk.timecapsule.exception.NotFoundException;
import dev.horbatiuk.timecapsule.exception.aws.InternalAwsException;
import dev.horbatiuk.timecapsule.exception.aws.s3.S3ActionException;
import dev.horbatiuk.timecapsule.exception.aws.scheduler.CreateScheduleException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.scheduler.model.GetScheduleResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCapsuleControllerTest {

    @Mock
    private CapsuleService capsuleService;

    @Mock
    private CapsuleRepository capsuleRepository;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private EventBridgeScheduledService eventBridgeScheduledService;

    @Mock
    private S3Service s3Service;

    @InjectMocks
    private AdminCapsuleController controller;

    @Captor
    private ArgumentCaptor<UUID> uuidCaptor;

    @BeforeEach
    void setUp() {

    }

    @Test
    void listCapsules_returnsPage() {
        CapsuleResponseDTO dto = new CapsuleResponseDTO();
        Page<CapsuleResponseDTO> page = new PageImpl<>(List.of(dto));
        when(capsuleService.findCapsulesByFilters(
                eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), any()))
                .thenReturn(page);

        ResponseEntity<Page<CapsuleResponseDTO>> response = controller.listCapsules(null, null, null, null, null, null, 0, 20, "createdAt,desc");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
        verify(capsuleService).findCapsulesByFilters(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void getCapsuleById_success() throws NotFoundException {
        UUID capsuleId = UUID.randomUUID();
        CapsuleResponseDTO dto = new CapsuleResponseDTO();
        when(capsuleService.findCapsuleById(capsuleId)).thenReturn(dto);

        ResponseEntity<CapsuleResponseDTO> response = controller.getCapsuleById(capsuleId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(dto, response.getBody());
        verify(capsuleService).findCapsuleById(capsuleId);
    }

    @Test
    void getCapsuleById_notFound_throwsAppException() throws NotFoundException {
        UUID capsuleId = UUID.randomUUID();
        when(capsuleService.findCapsuleById(capsuleId)).thenThrow(new NotFoundException("no"));

        AppException ex = assertThrows(AppException.class, () -> controller.getCapsuleById(capsuleId));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("Capsule not found", ex.getMessage());
    }

    @Test
    void getAttachments_success() throws NotFoundException {
        UUID capsuleId = UUID.randomUUID();
        when(capsuleService.findCapsuleById(capsuleId)).thenReturn(new CapsuleResponseDTO());
        List<AttachmentResponseDTO> attachments = List.of(new AttachmentResponseDTO());
        when(attachmentService.getAttachmentsByCapsuleId(capsuleId)).thenReturn(attachments);

        ResponseEntity<List<AttachmentResponseDTO>> response = controller.getAttachments(capsuleId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(attachments, response.getBody());
        verify(attachmentService).getAttachmentsByCapsuleId(capsuleId);
    }

    @Test
    void getByUserId_returnsCapsules() {
        UUID userId = UUID.randomUUID();
        List<CapsuleResponseDTO> capsules = List.of(new CapsuleResponseDTO());
        when(capsuleService.findCapsulesByUserId(userId)).thenReturn(capsules);

        ResponseEntity<List<CapsuleResponseDTO>> response = controller.getByUserId(userId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(capsules, response.getBody());
        verify(capsuleService).findCapsulesByUserId(userId);
    }

    @Test
    void getByEmail_returnsCapsules() {
        String email = "test@example.com";
        List<CapsuleResponseDTO> capsules = List.of(new CapsuleResponseDTO());
        when(capsuleService.findCapsulesByUserEmail(email)).thenReturn(capsules);

        ResponseEntity<List<CapsuleResponseDTO>> response = controller.getByEmail(email);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(capsules, response.getBody());
        verify(capsuleService).findCapsulesByUserEmail(email);
    }

    @Test
    void updateMetadata_callsService() throws NotFoundException {
        UUID capsuleId = UUID.randomUUID();
        EditCapsuleDTO dto = new EditCapsuleDTO();
        // assume service does not throw
        doNothing().when(capsuleService).editCapsuleAsAdmin(capsuleId, dto);

        ResponseEntity<Void> response = controller.updateMetadata(capsuleId, dto);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(capsuleService).editCapsuleAsAdmin(capsuleId, dto);
    }

    @Test
    void setStatus_deactivate_updatesExistingSchedule() throws Exception {
        UUID capsuleId = UUID.randomUUID();

        CapsuleResponseDTO capsule = new CapsuleResponseDTO();
        capsule.setStatus(CapsuleStatus.ACTIVE);
        capsule.setOpenAt(Timestamp.from(Instant.now().plusSeconds(3600))); // ✅ стабільно

        when(capsuleService.findCapsuleById(capsuleId)).thenReturn(capsule);

        GetScheduleResponse schedule = GetScheduleResponse.builder().build();
        when(eventBridgeScheduledService.getSchedule(capsuleId)).thenReturn(schedule);

        ResponseEntity<Void> response =
                controller.setStatus(capsuleId, CapsuleStatus.INACTIVE);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        verify(capsuleService)
                .setCapsuleStatus(capsuleId, CapsuleStatus.INACTIVE);

        verify(eventBridgeScheduledService)
                .updateSchedule(eq(schedule), eq(true), eq(capsuleId)); // ✅ TRUE
    }

    @Test
    void resyncCapsule_success() throws NotFoundException, CreateScheduleException, InternalAwsException, S3ActionException {
        UUID capsuleId = UUID.randomUUID();
        CapsuleResponseDTO dto = new CapsuleResponseDTO();
        dto.setOpenAt(Timestamp.from(Instant.now()));
        when(capsuleService.findCapsuleById(capsuleId)).thenReturn(dto);

        ResponseEntity<Void> response = controller.resyncCapsule(capsuleId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(s3Service).uploadCapsuleData(dto);
        verify(eventBridgeScheduledService).deleteSchedule(capsuleId);
        verify(eventBridgeScheduledService).createNewSchedule(eq(capsuleId), any());
    }

    @Test
    void deleteCapsule_success() throws NotFoundException, InternalAwsException, S3ActionException {
        UUID capsuleId = UUID.randomUUID();
        Capsule capsule = new Capsule();
        when(capsuleService.findCapsuleEntityById(capsuleId)).thenReturn(capsule);

        ResponseEntity<Void> response = controller.deleteCapsule(capsuleId);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(eventBridgeScheduledService).deleteSchedule(capsuleId);
        verify(attachmentService).deleteAllAttachmentsFromCapsule(capsuleId);
        verify(capsuleRepository).delete(capsule);
    }

    @Test
    void deleteCapsule_scheduleNotFound_skipsAndDeletes() throws NotFoundException, InternalAwsException {
        UUID capsuleId = UUID.randomUUID();
        Capsule capsule = new Capsule();
        doThrow(new NotFoundException("not")).when(eventBridgeScheduledService).deleteSchedule(capsuleId);
        when(capsuleService.findCapsuleEntityById(capsuleId)).thenReturn(capsule);

        ResponseEntity<Void> response = controller.deleteCapsule(capsuleId);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(eventBridgeScheduledService).deleteSchedule(capsuleId);
        verify(capsuleRepository).delete(capsule);
    }

    @Test
    void deleteByEmail_success() {
        String email = "user@example.com";
        doNothing().when(capsuleService).deleteAllCapsulesByEmail(email);

        ResponseEntity<Void> response = controller.deleteByEmail(email);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(capsuleService).deleteAllCapsulesByEmail(email);
    }

    @Test
    void deleteByEmail_badRequest_forBlank() {
        ResponseEntity<Void> response = controller.deleteByEmail("");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
