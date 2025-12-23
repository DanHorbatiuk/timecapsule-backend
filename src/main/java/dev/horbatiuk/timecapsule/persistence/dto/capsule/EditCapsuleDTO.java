package dev.horbatiuk.timecapsule.persistence.dto.capsule;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EditCapsuleDTO {

    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @Size(max = 10_000, message = "Description must not exceed 10,000 characters")
    private String description;

    @Future(message = "OpenAt must be in the future")
    private Timestamp openAt;
}
