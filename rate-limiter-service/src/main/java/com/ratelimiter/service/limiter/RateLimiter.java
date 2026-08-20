package com.ratelimiter.service.limiter;

/**
 * Core interface for all rate limiting algorithms.
 *
 * Every algorithm (Fixed Window, Token Bucket, Sliding Window Log,
 * Sliding Window Counter) implements this interface.
 *
 * The RateLimiterService uses a Factory to pick the right implementation
 * at runtime based on the rule's strategy field.
 */
public interface RateLimiter {

    /**
     * Check if the request is allowed. If allowed, the counter is incremented.
     *
     * @param key           The rate limit key (IP, userId, apiKey, etc.)
     * @param limit         Maximum number of requests allowed per window
     * @param windowSeconds Duration of the time window in seconds
     * @return RateLimitResult indicating allow/reject + metadata
     */
    RateLimitResult allow(String key, int limit, int windowSeconds);

    /**
     * Get the current count for a key without incrementing.
     * Useful for monitoring and dashboards.
     *
     * @param key           The rate limit key
     * @param windowSeconds Duration of the time window in seconds
     * @return Current count of requests in the window
     */
    long getCurrentCount(String key, int windowSeconds);

    /**
     * Reset the counter for a key.
     * Useful for testing and manual overrides.
     *
     * @param key The rate limit key to reset
     */
    void reset(String key);

    /**
     * Get the name of this algorithm.
     *
     * @return Algorithm name (e.g., "FIXED_WINDOW", "TOKEN_BUCKET")
     */
    String getAlgorithmName();
}
