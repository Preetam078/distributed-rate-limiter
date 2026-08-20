package com.ratelimiter.common.enums;

/**
 * How to extract the rate limiting key from a request.
 *
 * IP_ADDRESS:    Rate limit by client IP
 * USER_ID:       Rate limit by authenticated user ID
 * API_KEY:       Rate limit by API key
 * CLIENT_ID:     Rate limit by OAuth client ID
 */
public enum KeyResolverType {
    IP_ADDRESS,
    USER_ID,
    API_KEY,
    CLIENT_ID
}
