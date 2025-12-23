package dev.horbatiuk.timecapsule.persistence.dto.capsule;

import dev.horbatiuk.timecapsule.persistence.dto.attachment.AttachmentResponseDTO;
import dev.horbatiuk.timecapsule.persistence.entities.enums.CapsuleStatus;
import lombok.*;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class CapsuleResponseDTO {
    private UUID id;
    private String title;
    private String description;
    private CapsuleStatus status;
    private Timestamp createdAt;
    private Timestamp openAt;
    private String username;
    private String email;
    private List<AttachmentResponseDTO> attachments;
}

