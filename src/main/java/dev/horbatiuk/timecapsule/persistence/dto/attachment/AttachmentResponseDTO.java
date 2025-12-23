package dev.horbatiuk.timecapsule.persistence.dto.attachment;

import lombok.*;

import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class AttachmentResponseDTO {
    private UUID id;
    private String filename;
    private String description;
    private String fileKey;
    private UUID capsuleId;
}
