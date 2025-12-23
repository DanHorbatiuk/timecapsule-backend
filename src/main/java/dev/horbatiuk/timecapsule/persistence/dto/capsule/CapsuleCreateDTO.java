package dev.horbatiuk.timecapsule.persistence.dto.capsule;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CapsuleCreateDTO {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @Size(max = 10_000, message = "Description must not exceed 10,000 characters")
    private String description;

    @NotNull(message = "OpenAt timestamp is required")
    @Future(message = "OpenAt must be in the future")
    private Timestamp openAt;
}
