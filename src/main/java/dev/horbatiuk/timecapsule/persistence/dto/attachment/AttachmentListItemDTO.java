package dev.horbatiuk.timecapsule.persistence.dto.attachment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AttachmentListItemDTO {
    private UUID id;
    private String filename;
}
