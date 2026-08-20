package com.ratelimiter.common.enums;

/**
 * Available rate limiting algorithms.
 * Each algorithm is implemented in the Rate Limiter Service and can be
 * switched at runtime by changing the strategy field in a rule.
 */
public enum Algorithm {
    FIXED_WINDOW,
    TOKEN_BUCKET,
    SLIDING_WINDOW_LOG,
    SLIDING_WINDOW_COUNTER
}
