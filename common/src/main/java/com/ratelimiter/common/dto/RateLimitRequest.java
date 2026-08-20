package com.ratelimiter.common.dto;

import com.ratelimiter.common.enums.Algorithm;
import com.ratelimiter.common.enums.KeyResolverType;

/**
 * Request to check if a client is allowed to make a request.
 * Sent from API Gateway → Rate Limiter Service.
 */
public class RateLimitRequest {

    private String key;                // The resolved rate limit key (IP, userId, apiKey)
    private String path;               // The API path being accessed (e.g., /api/v1/users)
    private String method;             // HTTP method (GET, POST, etc.)
    private Algorithm algorithm;       // Which algorithm to use
    private int requestsPerWindow;     // Max requests allowed per window
    private int windowSizeSeconds;     // Window duration in seconds
    private KeyResolverType keyResolverType;

    public RateLimitRequest() {}

    public RateLimitRequest(String key, String path, String method,
                            Algorithm algorithm, int requestsPerWindow,
                            int windowSizeSeconds, KeyResolverType keyResolverType) {
        this.key = key;
        this.path = path;
        this.method = method;
        this.algorithm = algorithm;
        this.requestsPerWindow = requestsPerWindow;
        this.windowSizeSeconds = windowSizeSeconds;
        this.keyResolverType = keyResolverType;
    }

    // --- Getters and Setters ---

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public Algorithm getAlgorithm() { return algorithm; }
    public void setAlgorithm(Algorithm algorithm) { this.algorithm = algorithm; }

    public int getRequestsPerWindow() { return requestsPerWindow; }
    public void setRequestsPerWindow(int requestsPerWindow) { this.requestsPerWindow = requestsPerWindow; }

    public int getWindowSizeSeconds() { return windowSizeSeconds; }
    public void setWindowSizeSeconds(int windowSizeSeconds) { this.windowSizeSeconds = windowSizeSeconds; }

    public KeyResolverType getKeyResolverType() { return keyResolverType; }
    public void setKeyResolverType(KeyResolverType keyResolverType) { this.keyResolverType = keyResolverType; }

    @Override
    public String toString() {
        return "RateLimitRequest{" +
                "key='" + key + '\'' +
                ", path='" + path + '\'' +
                ", method='" + method + '\'' +
                ", algorithm=" + algorithm +
                ", requestsPerWindow=" + requestsPerWindow +
                ", windowSizeSeconds=" + windowSizeSeconds +
                '}';
    }
}
