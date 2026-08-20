package com.ratelimiter.common.dto;

/**
 * Response from Rate Limiter Service indicating whether a request is allowed.
 * Sent from Rate Limiter Service → API Gateway.
 */
public class RateLimitResponse {

    private boolean allowed;           // true = request allowed, false = rejected (429)
    private String key;                // The rate limit key that was checked
    private int remainingRequests;     // How many requests are left in the current window
    private long retryAfterSeconds;    // How long to wait before retrying (0 if allowed)
    private long totalRequests;        // Total requests seen in current window
    private int limit;                 // The configured limit

    public RateLimitResponse() {}

    public RateLimitResponse(boolean allowed, String key, int remainingRequests,
                             long retryAfterSeconds, long totalRequests, int limit) {
        this.allowed = allowed;
        this.key = key;
        this.remainingRequests = remainingRequests;
        this.retryAfterSeconds = retryAfterSeconds;
        this.totalRequests = totalRequests;
        this.limit = limit;
    }

    // --- Factory methods ---

    public static RateLimitResponse allow(String key, int remaining, long total, int limit) {
        return new RateLimitResponse(true, key, remaining, 0, total, limit);
    }

    public static RateLimitResponse reject(String key, long retryAfter, long total, int limit) {
        return new RateLimitResponse(false, key, 0, retryAfter, total, limit);
    }

    // --- Getters and Setters ---

    public boolean isAllowed() { return allowed; }
    public void setAllowed(boolean allowed) { this.allowed = allowed; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public int getRemainingRequests() { return remainingRequests; }
    public void setRemainingRequests(int remainingRequests) { this.remainingRequests = remainingRequests; }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
    public void setRetryAfterSeconds(long retryAfterSeconds) { this.retryAfterSeconds = retryAfterSeconds; }

    public long getTotalRequests() { return totalRequests; }
    public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }

    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }

    @Override
    public String toString() {
        return "RateLimitResponse{" +
                "allowed=" + allowed +
                ", key='" + key + '\'' +
                ", remainingRequests=" + remainingRequests +
                ", retryAfterSeconds=" + retryAfterSeconds +
                ", totalRequests=" + totalRequests +
                ", limit=" + limit +
                '}';
    }
}
