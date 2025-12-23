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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CapsuleService {

    private static final Logger logger = LoggerFactory.getLogger(CapsuleService.class);

    private final CapsuleRepository capsuleRepository;
    private final UserRepository userRepository;
    private final CapsuleMapper capsuleMapper;

    // ---------------- USER METHODS ----------------

    @Transactional
    public List<CapsuleResponseDTO> findCapsulesByEmail(String email) {
        User user = findUserByEmail(email);
        return capsuleRepository.findAllByAppUser(user).stream()
                .map(capsuleMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<CapsuleResponseDTO> findCapsulesByUserId(UUID userId) {
        return capsuleRepository.findAllByAppUser_Id(userId).stream()
                .map(capsuleMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public CapsuleResponseDTO addNewCapsule(String email, CapsuleCreateDTO dto) {
        User user = findUserByEmail(email);
        Capsule capsule = new Capsule();
        capsule.setTitle(dto.getTitle());
        capsule.setDescription(dto.getDescription());
        capsule.setOpenAt(dto.getOpenAt());
        capsule.setStatus(CapsuleStatus.DRAFT);
        capsule.setAppUser(user);

        Capsule saved = capsuleRepository.save(capsule);
        logger.info("Saved new capsule {} for user {}", saved.getId(), email);
        return capsuleMapper.toResponseDTO(saved);
    }

    @Transactional
    public boolean userHasAccess(UUID capsuleId, String email) {
        try {
            CapsuleResponseDTO capsule = findCapsuleById(capsuleId);
            return capsule.getEmail().equals(email);
        } catch (NotFoundException e) {
            logger.warn("Access check failed: capsule {} not found", capsuleId);
            return false;
        }
    }

    // ---------------- ADMIN METHODS ----------------

    @Transactional
    public List<CapsuleResponseDTO> findCapsulesByUserEmail(String email) {
        User user = findUserByEmail(email);
        return capsuleRepository.findAllByAppUser(user).stream()
                .map(capsuleMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<CapsuleResponseDTO> findCapsulesByFilters(
            String email,
            UUID userId,
            CapsuleStatus status,
            String title,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    ) {
        logger.debug("Finding capsules with filters - email: {}, userId: {}, status: {}, title: {}, from: {}, to: {}",
                email, userId, status, title, from, to);

        Page<Capsule> capsulesPage = capsuleRepository.findAllWithFilters(
                title,
                status,
                email,
                from != null ? from.toInstant() : null,
                to != null ? to.toInstant() : null,
                userId,
                pageable
        );

        return capsulesPage.map(capsuleMapper::toResponseDTO);
    }

    @Transactional
    public CapsuleResponseDTO findCapsuleById(UUID capsuleId) throws NotFoundException {
        return capsuleMapper.toResponseDTO(findCapsuleEntityById(capsuleId));
    }

    @Transactional
    public Capsule findCapsuleEntityById(UUID capsuleId) throws NotFoundException {
        return capsuleRepository.findByIdWithUser(capsuleId)
                .orElseThrow(() -> new NotFoundException("Capsule not found: " + capsuleId));
    }

    @Transactional
    public void setCapsuleStatus(UUID capsuleId, CapsuleStatus newStatus) throws NotFoundException {
        Capsule capsule = findCapsuleEntityById(capsuleId);
        capsule.setStatus(newStatus);
        logger.info("Capsule {} status set to {}", capsuleId, newStatus);
    }

    @Transactional
    public void editCapsule(UUID capsuleId, EditCapsuleDTO dto) throws NotFoundException {
        Capsule capsule = findCapsuleEntityById(capsuleId);
        if (StringUtils.hasText(dto.getTitle())) capsule.setTitle(dto.getTitle());
        if (StringUtils.hasText(dto.getDescription())) capsule.setDescription(dto.getDescription());
        if (dto.getOpenAt() != null && capsule.getStatus() != CapsuleStatus.ACTIVE) capsule.setOpenAt(dto.getOpenAt());
        logger.info("Edited capsule {}", capsuleId);
    }

    @Transactional
    public void editCapsuleAsAdmin(UUID capsuleId, EditCapsuleDTO dto) throws NotFoundException {
        Capsule capsule = findCapsuleEntityById(capsuleId);
        if (StringUtils.hasText(dto.getTitle())) capsule.setTitle(dto.getTitle());
        if (StringUtils.hasText(dto.getDescription())) capsule.setDescription(dto.getDescription());
        if (dto.getOpenAt() != null) capsule.setOpenAt(dto.getOpenAt());
        logger.info("Admin edited capsule {}", capsuleId);
    }

    @Transactional
    public void deleteAllCapsulesByEmail(String email) {
        User user = findUserByEmail(email);
        List<Capsule> capsules = capsuleRepository.findAllByAppUser(user);
        capsules.forEach(capsuleRepository::delete);
        logger.info("Deleted {} capsules for user {}", capsules.size(), email);
    }

    private User findUserByEmail(String email) {
        return userRepository.findUserByEmail(email)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));
    }
}
