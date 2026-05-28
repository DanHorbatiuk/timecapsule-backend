package dev.horbatiuk.timecapsule.controllers.admin;

import dev.horbatiuk.timecapsule.persistence.UserRepository;
import dev.horbatiuk.timecapsule.persistence.entities.enums.CapsuleStatus;
import dev.horbatiuk.timecapsule.service.CapsuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
@Tag(name = "Admin Stats", description = "Application metrics and statistics (admin only)")
@SecurityRequirement(name = "bearerAuth")
public class AdminStatsController {

    private final CapsuleService capsuleService;
    private final UserRepository userRepository;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    @Operation(summary = "Application statistics", description = "Returns counts of users and capsules by status")
    public ResponseEntity<Map<String, Object>> getStats() {
        long totalCapsules = capsuleService.countAllCapsules();
        long activeCapsules = capsuleService.countCapsulesByStatus(CapsuleStatus.ACTIVE);
        long draftCapsules = capsuleService.countCapsulesByStatus(CapsuleStatus.DRAFT);
        long totalUsers = userRepository.count();

        return ResponseEntity.ok(Map.of(
                "timestamp", Instant.now().toString(),
                "users", Map.of("total", totalUsers),
                "capsules", Map.of(
                        "total", totalCapsules,
                        "active", activeCapsules,
                        "draft", draftCapsules
                )
        ));
    }
}
