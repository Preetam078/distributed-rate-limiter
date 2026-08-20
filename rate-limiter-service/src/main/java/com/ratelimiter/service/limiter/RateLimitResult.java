package com.ratelimiter.service.limiter;

/**
 * Result returned by all rate limiting algorithms.
 * Contains all metadata needed for the response headers and logging.
 */
public class RateLimitResult {

    private final boolean allowed;
    private final long currentCount;
    private final int limit;
    private final int windowSeconds;
    private final long retryAfterSeconds;

    public RateLimitResult(boolean allowed, long currentCount, int limit,
                           int windowSeconds, long retryAfterSeconds) {
        this.allowed = allowed;
        this.currentCount = currentCount;
        this.limit = limit;
        this.windowSeconds = windowSeconds;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Create an ALLOW result.
     */
    public static RateLimitResult allow(long currentCount, int limit, int windowSeconds) {
        int remaining = Math.max(0, limit - (int) currentCount);
        return new RateLimitResult(true, currentCount, limit, windowSeconds, 0);
    }

    /**
     * Create a REJECT result.
     */
    public static RateLimitResult reject(long currentCount, int limit, int windowSeconds) {
        return new RateLimitResult(false, currentCount, limit, windowSeconds, windowSeconds);
    }

    // --- Getters ---

    public boolean isAllowed() { return allowed; }
    public long getCurrentCount() { return currentCount; }
    public int getLimit() { return limit; }
    public int getWindowSeconds() { return windowSeconds; }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
    public int getRemainingRequests() { return Math.max(0, limit - (int) currentCount); }

    @Override
    public String toString() {
        return "RateLimitResult{" +
                "allowed=" + allowed +
                ", currentCount=" + currentCount +
                ", limit=" + limit +
                ", windowSeconds=" + windowSeconds +
                ", remaining=" + getRemainingRequests() +
                '}';
    }
}
