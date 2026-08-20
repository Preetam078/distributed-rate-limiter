package com.ratelimiter.service;

import com.ratelimiter.common.enums.Algorithm;
import com.ratelimiter.service.limiter.RateLimitResult;
import com.ratelimiter.service.limiter.RateLimiterFactory;
import com.ratelimiter.service.limiter.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1 Test — Verify the skeleton works:
 * 1. Spring context loads
 * 2. RateLimiterFactory is created and has the NO_OP limiter
 * 3. The NoOpRateLimiter always allows requests
 * 4. All core DTOs work correctly
 */
@SpringBootTest
class RateLimiterServiceTest {

    @Autowired
    private RateLimiterFactory factory;

    @Test
    void contextLoads() {
        assertNotNull(factory, "RateLimiterFactory should be injected");
    }

    @Test
    void factoryHasAtLeastOneLimiter() {
        Map<Algorithm, RateLimiter> all = factory.getAll();
        assertFalse(all.isEmpty(), "Factory should have at least one limiter registered");
        System.out.println("Registered algorithms: " + all.keySet());
    }

    @Test
    void noOpLimiterAlwaysAllows() {
        RateLimiter limiter = factory.get(Algorithm.SLIDING_WINDOW_COUNTER);
        assertNotNull(limiter, "Should get a limiter (falls back to default)");

        RateLimitResult result = limiter.allow("test-key", 100, 60);
        assertTrue(result.isAllowed(), "NoOp limiter should always allow");
        assertEquals(100, result.getLimit());
        assertEquals(0, result.getCurrentCount());
    }

    @Test
    void noOpLimiterGetCurrentCountReturnsZero() {
        RateLimiter limiter = factory.getDefault();
        long count = limiter.getCurrentCount("any-key", 60);
        assertEquals(0, count, "NoOp limiter count should be 0");
    }

    @Test
    void noOpLimiterResetDoesNotThrow() {
        RateLimiter limiter = factory.getDefault();
        assertDoesNotThrow(() -> limiter.reset("any-key"));
    }

    @Test
    void rateLimitResultCreationWorks() {
        RateLimitResult allowResult = RateLimitResult.allow(5, 100, 60);
        assertTrue(allowResult.isAllowed());
        assertEquals(5, allowResult.getCurrentCount());
        assertEquals(95, allowResult.getRemainingRequests());

        RateLimitResult rejectResult = RateLimitResult.reject(101, 100, 60);
        assertFalse(rejectResult.isAllowed());
        assertEquals(101, rejectResult.getCurrentCount());
        assertEquals(0, rejectResult.getRemainingRequests());
    }
}
