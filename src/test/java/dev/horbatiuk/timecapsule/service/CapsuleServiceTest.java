package dev.horbatiuk.timecapsule.service;

import dev.horbatiuk.timecapsule.exception.NotFoundException;
import dev.horbatiuk.timecapsule.exception.controller.AppException;
import dev.horbatiuk.timecapsule.persistence.CapsuleRepository;
import dev.horbatiuk.timecapsule.persistence.UserRepository;
import dev.horbatiuk.timecapsule.persistence.dto.capsule.CapsuleCreateDTO;
import dev.horbatiuk.timecapsule.persistence.dto.capsule.CapsuleResponseDTO;
import dev.horbatiuk.timecapsule.persistence.dto.capsule.EditCapsuleDTO;
import dev.horbatiuk.timecapsule.persistence.entities.Capsule;
import dev.horbatiuk.timecapsule.persistence.entities.User;
import dev.horbatiuk.timecapsule.persistence.entities.enums.CapsuleStatus;
import dev.horbatiuk.timecapsule.persistence.mapper.CapsuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CapsuleServiceTest {

    @Mock
    private CapsuleRepository capsuleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CapsuleMapper capsuleMapper;

    @InjectMocks
    private CapsuleService capsuleService;

    private User user;
    private Capsule capsule;
    private UUID capsuleId;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");

        capsule = new Capsule();
        capsuleId = UUID.randomUUID();
        capsule.setId(capsuleId);
        capsule.setAppUser(user);
    }

    @Test
    void testFindCapsulesByFilters_ShouldReturnPage() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        CapsuleStatus status = CapsuleStatus.ACTIVE;
        String title = "Test Title";

        OffsetDateTime from = OffsetDateTime.now().minusDays(1);
        OffsetDateTime to = OffsetDateTime.now().plusDays(1);

        Capsule capsule1 = new Capsule();
        Capsule capsule2 = new Capsule();
        List<Capsule> capsules = List.of(capsule1, capsule2);

        CapsuleResponseDTO dto1 = new CapsuleResponseDTO();
        CapsuleResponseDTO dto2 = new CapsuleResponseDTO();

        when(capsuleRepository.findAllWithFilters(
                title,
                status,
                email,
                from.toInstant(),
                to.toInstant(),
                userId,
                PageRequest.of(0, 10)
        )).thenReturn(new PageImpl<>(capsules));

        when(capsuleMapper.toResponseDTO(capsule1)).thenReturn(dto1);
        when(capsuleMapper.toResponseDTO(capsule2)).thenReturn(dto2);

        Page<CapsuleResponseDTO> result = capsuleService.findCapsulesByFilters(
                email,
                userId,
                status,
                title,
                from,
                to,
                PageRequest.of(0, 10)
        );

        assertEquals(2, result.getContent().size());

        verify(capsuleRepository).findAllWithFilters(
                title,
                status,
                email,
                from.toInstant(),
                to.toInstant(),
                userId,
                PageRequest.of(0, 10)
        );
    }

    @Test
    void testFindCapsulesByEmail_ShouldReturnCapsules() {
        when(userRepository.findUserByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(capsuleRepository.findAllByAppUser(user)).thenReturn(List.of(capsule));
        CapsuleResponseDTO dto = new CapsuleResponseDTO();
        when(capsuleMapper.toResponseDTO(capsule)).thenReturn(dto);

        List<CapsuleResponseDTO> result = capsuleService.findCapsulesByEmail(user.getEmail());

        assertEquals(1, result.size());
        verify(capsuleRepository).findAllByAppUser(user);
    }

    @Test
    void testAddNewCapsule_ShouldMapAndSave() {
        CapsuleCreateDTO dto = new CapsuleCreateDTO();
        Capsule savedCapsule = new Capsule();
        savedCapsule.setId(UUID.randomUUID());

        when(userRepository.findUserByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));
        when(capsuleRepository.save(any(Capsule.class)))
                .thenReturn(savedCapsule);

        CapsuleResponseDTO response = new CapsuleResponseDTO();
        when(capsuleMapper.toResponseDTO(savedCapsule))
                .thenReturn(response);

        CapsuleResponseDTO result =
                capsuleService.addNewCapsule(user.getEmail(), dto);

        assertNotNull(result);
        verify(capsuleRepository).save(any(Capsule.class));
    }

    @Test
    void testSetCapsuleStatus_ShouldUpdateStatus() throws NotFoundException {
        when(capsuleRepository.findByIdWithUser(capsuleId))
                .thenReturn(Optional.of(capsule));

        capsuleService.setCapsuleStatus(capsuleId, CapsuleStatus.ACTIVE);

        assertEquals(CapsuleStatus.ACTIVE, capsule.getStatus());
    }

    @Test
    void testUserHasAccess_ShouldReturnTrue() {
        CapsuleResponseDTO dto = new CapsuleResponseDTO();
        dto.setEmail(user.getEmail());

        when(capsuleRepository.findByIdWithUser(capsuleId))
                .thenReturn(Optional.of(capsule));
        when(capsuleMapper.toResponseDTO(capsule))
                .thenReturn(dto);

        boolean hasAccess = capsuleService.userHasAccess(capsuleId, user.getEmail());

        assertTrue(hasAccess);
    }

    @Test
    void testUserHasAccess_ShouldReturnFalse_WhenCapsuleNotFound() {
        when(capsuleRepository.findByIdWithUser(capsuleId))
                .thenReturn(Optional.empty());
        boolean result = capsuleService.userHasAccess(capsuleId, user.getEmail());
        assertFalse(result);
    }

    @Test
    void testEditCapsule_ShouldUpdateFields() throws NotFoundException {
        capsule.setStatus(CapsuleStatus.INACTIVE);

        when(capsuleRepository.findByIdWithUser(capsuleId))
                .thenReturn(Optional.of(capsule));

        EditCapsuleDTO dto = new EditCapsuleDTO();
        dto.setTitle("New Title");
        dto.setDescription("New Description");
        dto.setOpenAt(Timestamp.valueOf(LocalDateTime.of(2030, 1, 1, 0, 0)));

        capsuleService.editCapsule(capsuleId, dto);

        assertEquals("New Title", capsule.getTitle());
        assertEquals("New Description", capsule.getDescription());
        assertEquals(dto.getOpenAt(), capsule.getOpenAt());
    }

    @Test
    void testFindCapsulesByUserId_ShouldReturnListOfDTOs() {
        UUID userId = UUID.randomUUID();
        Capsule capsule1 = new Capsule();
        Capsule capsule2 = new Capsule();
        List<Capsule> capsules = List.of(capsule1, capsule2);

        CapsuleResponseDTO dto1 = new CapsuleResponseDTO();
        CapsuleResponseDTO dto2 = new CapsuleResponseDTO();

        when(capsuleRepository.findAllByAppUser_Id(userId)).thenReturn(capsules);
        when(capsuleMapper.toResponseDTO(capsule1)).thenReturn(dto1);
        when(capsuleMapper.toResponseDTO(capsule2)).thenReturn(dto2);

        List<CapsuleResponseDTO> result = capsuleService.findCapsulesByUserId(userId);

        assertEquals(2, result.size());
    }

    @Test
    void findUser_ShouldThrowAppException() {
        when(userRepository.findUserByEmail(user.getEmail())).thenReturn(Optional.empty());
        assertThrows(AppException.class, () -> capsuleService.findCapsulesByUserEmail(user.getEmail()));
    }
}
