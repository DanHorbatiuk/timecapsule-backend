package dev.horbatiuk.timecapsule.persistence;

import dev.horbatiuk.timecapsule.persistence.entities.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    List<Attachment> findByCapsuleId(UUID capsuleId);
    void deleteByCapsuleId(UUID capsuleId);
}
