package dev.horbatiuk.timecapsule.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Application health check")
public class HealthController {

    @Value("${info.app.version:1.0.0}")
    private String appVersion;

    @GetMapping
    @Operation(summary = "Health check", description = "Returns application status and version. No authentication required.")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "version", appVersion,
                "timestamp", Instant.now().toString()
        ));
    }
}
