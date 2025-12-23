package dev.horbatiuk.timecapsule.persistence.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateUserInfoRequestDTO {

    @NotBlank(message = "Name must not be blank")
    @Size(max = 50, message = "Name must be at most 50 characters")
    private String name;

    @Size(min = 6, message = "Old password must be at least 6 characters")
    private String oldPassword;

    @Size(min = 6, message = "New password must be at least 6 characters")
    private String newPassword;
}
