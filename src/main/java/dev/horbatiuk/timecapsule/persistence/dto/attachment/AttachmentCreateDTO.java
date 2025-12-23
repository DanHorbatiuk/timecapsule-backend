package dev.horbatiuk.timecapsule.persistence.dto.attachment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AttachmentCreateDTO {
    private String filename;
    private String description;
}
