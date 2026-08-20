package com.ratelimiter.common.enums;

/**
 * What to do when the rate limiter service is unavailable.
 *
 * FAIL_OPEN:  Allow all traffic (favor availability)
 * FAIL_CLOSED: Block all traffic (favor safety)
 */
public enum FailStrategy {
    FAIL_OPEN,
    FAIL_CLOSED
}
