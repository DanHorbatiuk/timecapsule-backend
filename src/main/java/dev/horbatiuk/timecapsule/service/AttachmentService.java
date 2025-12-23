package dev.horbatiuk.timecapsule.service;

import dev.horbatiuk.timecapsule.exception.NotFoundException;
import dev.horbatiuk.timecapsule.exception.aws.s3.S3ActionException;
import dev.horbatiuk.timecapsule.persistence.AttachmentRepository;
import dev.horbatiuk.timecapsule.persistence.CapsuleRepository;
import dev.horbatiuk.timecapsule.persistence.dto.attachment.AttachmentResponseDTO;
import dev.horbatiuk.timecapsule.persistence.entities.Attachment;
import dev.horbatiuk.timecapsule.persistence.entities.Capsule;
import dev.horbatiuk.timecapsule.persistence.entities.enums.CapsuleStatus;
import dev.horbatiuk.timecapsule.persistence.mapper.AttachmentMapper;
import dev.horbatiuk.timecapsule.service.aws.S3Service;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentService.class);

    private final AttachmentRepository attachmentRepository;
    private final AttachmentMapper attachmentMapper;
    private final S3Service s3Service;
    private final CapsuleRepository capsuleRepository;


    @Transactional
    public List<AttachmentResponseDTO> getAttachmentsByCapsuleId(UUID capsuleId) {
        logger.debug("Fetching attachments for capsule: {}", capsuleId);
        return attachmentRepository.findByCapsuleId(capsuleId).stream()
                .map(attachmentMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void addAttachmentToCapsule(UUID capsuleId, String description, String email, MultipartFile file)
            throws S3ActionException, IOException, NotFoundException {

        if (file == null || file.isEmpty()) {
            throw new IOException("Attachment file is missing");
        }

        Capsule capsuleEntity = capsuleRepository.findByIdWithUser(capsuleId)
                .orElseThrow(() -> new NotFoundException("Capsule not found"));

        if (!capsuleEntity.getAppUser().getEmail().equals(email)) {
            throw new AccessDeniedException("User does not have access to this capsule");
        }

        if (capsuleEntity.getStatus().equals(CapsuleStatus.ACTIVE)) {
            throw new AccessDeniedException("Cannot modify an active capsule");
        }

        // Генерація безпечного імені файлу
        String originalFilename = Optional.ofNullable(file.getOriginalFilename()).orElse("unnamed_file");
        String safeFilename = UUID.randomUUID() + "_" + originalFilename;

        // Завантаження в S3
        s3Service.uploadFile(
                capsuleId.toString(),
                safeFilename,
                file.getInputStream(),
                file.getSize(),
                file.getContentType()
        );

        // Збереження в базу
        Attachment attachment = Attachment.builder()
                .capsule(capsuleEntity)
                .filename(originalFilename)
                .fileKey(safeFilename)
                .description(description)
                .build();

        try {
            attachmentRepository.save(attachment);
        } catch (Exception e) {
            // rollback S3
            s3Service.deleteFile(capsuleId.toString(), safeFilename);
            throw new PersistenceException("Failed to save attachment", e);
        }
    }

    @Transactional
    public void deleteAttachmentFromCapsule(UUID capsuleId, UUID attachmentId, String userEmail) throws S3ActionException {
        logger.info("Deleting attachment: {} from capsule: {} by user: {}", attachmentId, capsuleId, userEmail);

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> {
                    logger.warn("Attachment not found: {}", attachmentId);
                    return new IllegalArgumentException("Attachment not found");
                });

        Capsule capsule = attachment.getCapsule();
        if (capsule == null || !capsule.getId().equals(capsuleId)) {
            logger.warn("Mismatch capsule ID for attachment. Expected: {}, Found: {}", capsuleId, capsule != null ? capsule.getId() : null);
            throw new IllegalArgumentException("Attachment does not belong to this capsule");
        }

        if (capsule.getStatus().equals(CapsuleStatus.ACTIVE)) {
            logger.warn("Attempt to delete from ACTIVE capsule: {}", capsuleId);
            throw new IllegalArgumentException("Capsule status is already active");
        }

        if (!capsule.getAppUser().getEmail().equals(userEmail)) {
            logger.warn("Unauthorized delete attempt by user: {} for capsule: {}", userEmail, capsuleId);
            throw new SecurityException("User does not have access to this capsule");
        }

        try {
            s3Service.deleteFile(capsuleId.toString(), attachment.getFileKey());
            logger.info("File deleted from S3: {}/{}", capsuleId, attachment.getFileKey());
        } catch (Exception e) {
            logger.error("S3 file deletion failed: {}/{}", capsuleId, attachment.getFileKey(), e);
            throw new S3ActionException("Failed to delete file from S3", e);
        }

        attachmentRepository.delete(attachment);
        logger.info("Attachment deleted from DB: {}", attachmentId);
    }

    @Transactional
    public void deleteAllAttachmentsFromCapsule(UUID capsuleId) throws S3ActionException {
        logger.info("Deleting all attachments from capsule: {}", capsuleId);

        List<Attachment> attachments = attachmentRepository.findByCapsuleId(capsuleId);
        if (attachments.isEmpty()) {
            logger.info("No attachments found for capsule: {}", capsuleId);
            return;
        }
        for (Attachment attachment : attachments) {
            try {
                s3Service.deleteFile(capsuleId.toString(), attachment.getFileKey());
                logger.info("File deleted from S3 {}/{}", capsuleId, attachment.getFileKey());
            } catch (Exception e) {
                logger.error("Failed to delete file from S3: {}/{}", capsuleId, attachment.getFileKey(), e);
                throw new S3ActionException("Failed to delete file from S3", e);
            }
        }
        attachmentRepository.deleteByCapsuleId(capsuleId);
        logger.info("All attachments deleted from DB for capsule: {}", capsuleId);
    }

}