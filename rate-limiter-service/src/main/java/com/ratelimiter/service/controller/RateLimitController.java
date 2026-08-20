package com.ratelimiter.service.controller;

import com.ratelimiter.common.dto.RateLimitRequest;
import com.ratelimiter.common.dto.RateLimitResponse;
import com.ratelimiter.service.limiter.RateLimitResult;
import com.ratelimiter.service.limiter.RateLimiterFactory;
import com.ratelimiter.common.enums.Algorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller that exposes the rate limiting check API.
 *
 * API Gateway calls: POST /api/v1/rl/check
 * to verify if a client is allowed to make a request.
 */
@RestController
@RequestMapping("/api/v1/rl")
public class RateLimitController {

    private static final Logger log = LoggerFactory.getLogger(RateLimitController.class);

    private final RateLimiterFactory factory;

    public RateLimitController(RateLimiterFactory factory) {
        this.factory = factory;
    }

    /**
     * Check if a request is allowed.
     * This is the main endpoint called by the API Gateway.
     *
     * POST /api/v1/rl/check
     * Body: RateLimitRequest (key, path, method, algorithm, limit, window)
     * Returns: RateLimitResponse (allowed, remaining, retryAfter, etc.)
     */
    @PostMapping("/check")
    public ResponseEntity<RateLimitResponse> checkRateLimit(@RequestBody RateLimitRequest request) {
        log.debug("Rate limit check: key={}, path={}, algorithm={}", 
                  request.getKey(), request.getPath(), request.getAlgorithm());

        // Pick the right algorithm from the factory
        Algorithm algorithm = request.getAlgorithm() != null
                ? request.getAlgorithm()
                : Algorithm.SLIDING_WINDOW_COUNTER;

        var limiter = factory.get(algorithm);

        // Run the rate limit check
        RateLimitResult result = limiter.allow(
                request.getKey(),
                request.getRequestsPerWindow(),
                request.getWindowSizeSeconds()
        );

        // Convert to response DTO
        RateLimitResponse response;
        if (result.isAllowed()) {
            response = RateLimitResponse.allow(
                    request.getKey(),
                    result.getRemainingRequests(),
                    result.getCurrentCount(),
                    result.getLimit()
            );
            log.debug("ALLOWED: key={}, remaining={}", request.getKey(), result.getRemainingRequests());
        } else {
            response = RateLimitResponse.reject(
                    request.getKey(),
                    result.getRetryAfterSeconds(),
                    result.getCurrentCount(),
                    result.getLimit()
            );
            log.debug("REJECTED: key={}, retryAfter={}s", request.getKey(), result.getRetryAfterSeconds());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get current usage for a key (read-only, doesn't consume).
     * Useful for monitoring dashboards.
     */
    @GetMapping("/usage")
    public ResponseEntity<RateLimitResponse> getUsage(
            @RequestParam String key,
            @RequestParam(defaultValue = "SLIDING_WINDOW_COUNTER") Algorithm algorithm,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "60") int windowSeconds) {

        var limiter = factory.get(algorithm);
        long currentCount = limiter.getCurrentCount(key, windowSeconds);

        RateLimitResponse response = RateLimitResponse.allow(
                key,
                Math.max(0, limit - (int) currentCount),
                currentCount,
                limit
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Reset the counter for a key.
     * Useful for testing and manual overrides.
     */
    @DeleteMapping("/reset/{key}")
    public ResponseEntity<String> resetCounter(
            @PathVariable String key,
            @RequestParam(defaultValue = "SLIDING_WINDOW_COUNTER") Algorithm algorithm) {

        var limiter = factory.get(algorithm);
        limiter.reset(key);

        log.info("Reset counter for key={}", key);
        return ResponseEntity.ok("Counter reset for key: " + key);
    }

    /**
     * Health check — list available algorithms.
     */
    @GetMapping("/algorithms")
    public ResponseEntity<?> getAlgorithms() {
        return ResponseEntity.ok(factory.getAll().keySet());
    }
}
