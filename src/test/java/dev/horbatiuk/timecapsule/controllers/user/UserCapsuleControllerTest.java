package dev.horbatiuk.timecapsule.controllers.user;

import dev.horbatiuk.timecapsule.exception.NotFoundException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserCapsuleControllerTest {

    @InjectMocks
    private UserCapsuleController controller;

    @Mock
    private CapsuleRepository capsuleRepository;

    @Mock
    private CapsuleService capsuleService;

    @Mock
    private S3Service s3Service;

    @Mock
    private EventBridgeScheduledService eventBridgeScheduledService;

    private CustomUserDetails userDetails;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        userDetails = mock(CustomUserDetails.class);
        when(userDetails.getEmail()).thenReturn("user@example.com");
    }

    @Test
    void getCapsulesByUser_shouldReturnList() {
        List<CapsuleResponseDTO> list = List.of(new CapsuleResponseDTO());
        when(capsuleService.findCapsulesByEmail("user@example.com")).thenReturn(list);

        ResponseEntity<List<CapsuleResponseDTO>> response = controller.getCapsulesByUser(userDetails);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void getCapsulesByUser_shouldReturnEmptyList() {
        when(capsuleService.findCapsulesByEmail("user@example.com")).thenReturn(Collections.emptyList());

        ResponseEntity<List<CapsuleResponseDTO>> response = controller.getCapsulesByUser(userDetails);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void addNewCapsuleByUser_shouldAddSuccessfully() {
        CapsuleCreateDTO dto = new CapsuleCreateDTO();

        CapsuleResponseDTO responseDTO = new CapsuleResponseDTO();
        responseDTO.setStatus(CapsuleStatus.DRAFT);
        responseDTO.setId(UUID.randomUUID());
        responseDTO.setTitle("Test capsule");

        when(userDetails.getEmail()).thenReturn("user@example.com");
        when(userDetails.isVerified()).thenReturn(true);
        when(userDetails.isPremiumUser()).thenReturn(false);
        when(capsuleRepository.countByAppUserEmail("user@example.com")).thenReturn(1L);
        when(capsuleService.addNewCapsule("user@example.com", dto))
                .thenReturn(responseDTO);
        ReflectionTestUtils.setField(controller, "userMaxCapsules", 5);
        ReflectionTestUtils.setField(controller, "premiumUserMaxCapsules", 10);
        ResponseEntity<CapsuleResponseDTO> response =
                controller.addNewCapsuleByUser(userDetails, dto);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(responseDTO.getId(), response.getBody().getId());
        verify(capsuleService).addNewCapsule("user@example.com", dto);
    }

    @Test
    void addNewCapsuleByUser_shouldFail_ifUnverified() {
        when(userDetails.isVerified()).thenReturn(false);

        AppException exception = assertThrows(AppException.class, () ->
                controller.addNewCapsuleByUser(userDetails, new CapsuleCreateDTO())
        );
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void addNewCapsuleByUser_shouldFail_ifLimitExceeded() {
        when(userDetails.isVerified()).thenReturn(true);
        when(userDetails.isPremiumUser()).thenReturn(false);
        when(capsuleRepository.countByAppUserEmail("user@example.com")).thenReturn(3L);

        AppException exception = assertThrows(AppException.class, () ->
                controller.addNewCapsuleByUser(userDetails, new CapsuleCreateDTO())
        );
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void changeCapsuleStatusToActive_shouldWork() throws Exception {
        UUID capsuleId = UUID.randomUUID();
        CapsuleResponseDTO dto = new CapsuleResponseDTO();
        dto.setOpenAt(Timestamp.valueOf(LocalDateTime.now()));

        when(capsuleService.userHasAccess(capsuleId, "user@example.com")).thenReturn(true);
        when(capsuleService.findCapsuleById(capsuleId)).thenReturn(dto);

        ResponseEntity<Void> response = controller.changeCapsuleStatusToActive(userDetails, capsuleId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(s3Service).uploadCapsuleData(dto);
        verify(eventBridgeScheduledService).createNewSchedule(eq(capsuleId), any(Instant.class));
    }

    @Test
    void changeCapsuleStatusToActive_shouldFail_ifNoAccess() {
        UUID capsuleId = UUID.randomUUID();
        when(capsuleService.userHasAccess(capsuleId, "user@example.com")).thenReturn(false);

        AppException exception = assertThrows(AppException.class, () ->
                controller.changeCapsuleStatusToActive(userDetails, capsuleId)
        );
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void editCapsule_shouldSucceed() throws NotFoundException {
        UUID capsuleId = UUID.randomUUID();
        EditCapsuleDTO dto = new EditCapsuleDTO();

        when(userDetails.getEmail()).thenReturn("user@example.com");
        when(capsuleService.userHasAccess(capsuleId, "user@example.com")).thenReturn(true);
        CapsuleResponseDTO capsule = mock(CapsuleResponseDTO.class);
        when(capsule.getStatus()).thenReturn(CapsuleStatus.INACTIVE);
        when(capsuleService.findCapsuleById(capsuleId)).thenReturn(capsule);

        ResponseEntity<Void> response = controller.editCapsule(userDetails, capsuleId, dto);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(capsuleService).editCapsule(capsuleId, dto);
    }

    @Test
    void editCapsule_shouldFail_ifNotFound() throws NotFoundException {
        UUID capsuleId = UUID.randomUUID();
        EditCapsuleDTO dto = new EditCapsuleDTO();

        when(capsuleService.userHasAccess(capsuleId, "user@example.com")).thenReturn(true);
        when(userDetails.getEmail()).thenReturn("user@example.com");
        when(capsuleService.findCapsuleById(capsuleId))
                .thenThrow(new dev.horbatiuk.timecapsule.exception.NotFoundException("Not found"));

        AppException exception = assertThrows(AppException.class, () ->
                controller.editCapsule(userDetails, capsuleId, dto)
        );
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void editCapsule_shouldFail_ifNoAccess() {
        UUID capsuleId = UUID.randomUUID();

        when(capsuleService.userHasAccess(capsuleId, "user@example.com")).thenReturn(false);

        AppException exception = assertThrows(AppException.class, () ->
                controller.editCapsule(userDetails, capsuleId, new EditCapsuleDTO())
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }
}
