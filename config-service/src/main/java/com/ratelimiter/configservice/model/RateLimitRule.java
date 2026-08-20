package com.ratelimiter.configservice.model;

import com.ratelimiter.common.enums.Algorithm;
import com.ratelimiter.common.enums.FailStrategy;
import com.ratelimiter.common.enums.KeyResolverType;
import jakarta.persistence.*;

/**
 * JPA entity representing a rate limit rule.
 * Stored in the database and served via REST API.
 */
@Entity
@Table(name = "rate_limit_rules")
public class RateLimitRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String domain;              // Which service this rule applies to

    @Column(nullable = false)
    private String path;                // API path pattern (e.g., /api/v1/users/*)

    @Column(nullable = false)
    private String method;              // HTTP method (GET, POST, ALL)

    @Column(nullable = false)
    private int requestsPerWindow;      // Max requests allowed per window

    @Column(nullable = false)
    private int windowSizeSeconds;      // Window duration in seconds

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KeyResolverType keyResolverType = KeyResolverType.IP_ADDRESS;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Algorithm algorithm = Algorithm.SLIDING_WINDOW_COUNTER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FailStrategy failStrategy = FailStrategy.FAIL_OPEN;

    private int priority = 0;           // Higher priority = matched first

    @Column(nullable = false)
    private boolean enabled = true;

    // --- Constructors ---

    public RateLimitRule() {}

    public RateLimitRule(String domain, String path, String method,
                         int requestsPerWindow, int windowSizeSeconds) {
        this.domain = domain;
        this.path = path;
        this.method = method;
        this.requestsPerWindow = requestsPerWindow;
        this.windowSizeSeconds = windowSizeSeconds;
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public int getRequestsPerWindow() { return requestsPerWindow; }
    public void setRequestsPerWindow(int requestsPerWindow) { this.requestsPerWindow = requestsPerWindow; }

    public int getWindowSizeSeconds() { return windowSizeSeconds; }
    public void setWindowSizeSeconds(int windowSizeSeconds) { this.windowSizeSeconds = windowSizeSeconds; }

    public KeyResolverType getKeyResolverType() { return keyResolverType; }
    public void setKeyResolverType(KeyResolverType keyResolverType) { this.keyResolverType = keyResolverType; }

    public Algorithm getAlgorithm() { return algorithm; }
    public void setAlgorithm(Algorithm algorithm) { this.algorithm = algorithm; }

    public FailStrategy getFailStrategy() { return failStrategy; }
    public void setFailStrategy(FailStrategy failStrategy) { this.failStrategy = failStrategy; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @Override
    public String toString() {
        return "RateLimitRule{" +
                "id=" + id +
                ", domain='" + domain + '\'' +
                ", path='" + path + '\'' +
                ", method='" + method + '\'' +
                ", requestsPerWindow=" + requestsPerWindow +
                ", windowSizeSeconds=" + windowSizeSeconds +
                ", algorithm=" + algorithm +
                '}';
    }
}
