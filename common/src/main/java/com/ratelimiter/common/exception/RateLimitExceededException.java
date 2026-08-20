package com.ratelimiter.common.exception;

/**
 * Thrown when a request exceeds the configured rate limit.
 * In the API Gateway, this results in HTTP 429 Too Many Requests.
 */
public class RateLimitExceededException extends RuntimeException {

    private final String key;
    private final long retryAfterSeconds;
    private final int limit;
    private final long totalRequests;

    public RateLimitExceededException(String key, long retryAfterSeconds,
                                      int limit, long totalRequests) {
        super("Rate limit exceeded for key: " + key +
              ". Limit: " + limit +
              ". Current usage: " + totalRequests +
              ". Retry after: " + retryAfterSeconds + " seconds.");
        this.key = key;
        this.retryAfterSeconds = retryAfterSeconds;
        this.limit = limit;
        this.totalRequests = totalRequests;
    }

    public String getKey() { return key; }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
    public int getLimit() { return limit; }
    public long getTotalRequests() { return totalRequests; }
}
