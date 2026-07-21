package com.company.outbound.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
class HealthController {
    @GetMapping("/api/health")
    Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
