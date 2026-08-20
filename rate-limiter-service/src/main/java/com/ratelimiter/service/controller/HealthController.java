package com.ratelimiter.service.controller;

import com.ratelimiter.service.limiter.RateLimiterFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final RateLimiterFactory factory;

    public HealthController(RateLimiterFactory factory) {
        this.factory = factory;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "rate-limiter-service",
                "algorithms", factory.getAll().keySet()
        ));
    }
}
