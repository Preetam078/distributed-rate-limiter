package com.ratelimiter.service.limiter;

import com.ratelimiter.common.enums.Algorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory that picks the right rate limiting algorithm at runtime.
 *
 * All algorithms implement the RateLimiter interface. This factory
 * maps algorithm names to their implementations. When a rule specifies
 * a strategy, the factory returns the correct implementation.
 *
 * Example:
 *   RateLimiter limiter = factory.get(Algorithm.TOKEN_BUCKET);
 *   RateLimitResult result = limiter.allow("192.168.1.1", 100, 60);
 */
@Component
public class RateLimiterFactory {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterFactory.class);

    private final Map<Algorithm, RateLimiter> limiters = new HashMap<>();

    public RateLimiterFactory(List<RateLimiter> allLimiters) {
        for (RateLimiter limiter : allLimiters) {
            try {
                Algorithm algorithm = Algorithm.valueOf(limiter.getAlgorithmName());
                limiters.put(algorithm, limiter);
                log.info("Registered rate limiter: {} -> {}", algorithm, limiter.getClass().getSimpleName());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown algorithm name: {} — skipping registration", limiter.getAlgorithmName());
            }
        }
        log.info("RateLimiterFactory initialized with {} algorithms: {}", limiters.size(), limiters.keySet());
    }

    /**
     * Get the rate limiter for the given algorithm.
     * Falls back to SLIDING_WINDOW_COUNTER if the algorithm is not found.
     *
     * @param algorithm The algorithm to use
     * @return The corresponding RateLimiter implementation
     */
    public RateLimiter get(Algorithm algorithm) {
        RateLimiter limiter = limiters.get(algorithm);
        if (limiter == null) {
            log.warn("Algorithm {} not found, falling back to SLIDING_WINDOW_COUNTER", algorithm);
            limiter = limiters.get(Algorithm.SLIDING_WINDOW_COUNTER);
        }
        if (limiter == null) {
            throw new IllegalStateException("No rate limiter implementations found! Check your Spring configuration.");
        }
        return limiter;
    }

    /**
     * Get the default rate limiter (SLIDING_WINDOW_COUNTER).
     */
    public RateLimiter getDefault() {
        return get(Algorithm.SLIDING_WINDOW_COUNTER);
    }

    /**
     * List all registered algorithms.
     */
    public Map<Algorithm, RateLimiter> getAll() {
        return Map.copyOf(limiters);
    }
}
