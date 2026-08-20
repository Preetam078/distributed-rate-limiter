package com.ratelimiter.service.limiter.impl;

import com.ratelimiter.service.limiter.RateLimiter;
import com.ratelimiter.service.limiter.RateLimitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * STUB implementation — always allows requests.
 * This exists ONLY so Phase 1 compiles and the skeleton runs.
 *
 * It will be replaced with real algorithms in Phase 2+.
 */
@Component
public class NoOpRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(NoOpRateLimiter.class);

    @Override
    public RateLimitResult allow(String key, int limit, int windowSeconds) {
        log.debug("NoOp: always allowing key={}", key);
        return RateLimitResult.allow(0, limit, windowSeconds);
    }

    @Override
    public long getCurrentCount(String key, int windowSeconds) {
        return 0;
    }

    @Override
    public void reset(String key) {
        log.debug("NoOp: reset called for key={} (no-op)", key);
    }

    @Override
    public String getAlgorithmName() {
        return "SLIDING_WINDOW_COUNTER";  // Claims to be the default — replaced in Phase 2
    }
}
