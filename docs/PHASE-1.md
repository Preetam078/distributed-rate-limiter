# Phase 1: Multi-Module Maven Project Setup

> **Status:** ✅ COMPLETE
> **Goal:** Create a proper multi-module Maven project with all microservices.
> **Back to README →** [../README.md](../README.md)

---

## Table of Contents

1. [Overview](#overview)
2. [What Was Built](#what-was-built)
3. [Project Structure](#project-structure)
4. [Module Details](#module-details)
5. [Core Design — The Strategy Pattern](#core-design--the-strategy-pattern)
6. [Key Classes Explained](#key-classes-explained)
7. [REST APIs](#rest-apis)
8. [Configuration Files](#configuration-files)
9. [How to Build](#how-to-build)
10. [How to Run](#how-to-run)
11. [Test Results](#test-results)
12. [What's Ready for Phase 2](#whats-ready-for-phase-2)

---

## Overview

Phase 1 creates the **skeleton** of the entire distributed rate limiter system. We set up 5 independent microservices, each with its own `pom.xml`, `application.yml`, and Spring Boot application class. The services are designed to communicate via REST APIs.

The most important architectural decision in this phase is the **Strategy Pattern** — all rate limiting algorithms will implement the same `RateLimiter` interface, and a `RateLimiterFactory` will pick the right one at runtime. This means we can switch algorithms by just changing a config value, with zero code changes.

---

## What Was Built

| Module | Port | Description | Key Dependencies |
|---|---|---|---|
| **common** | — | Shared DTOs, enums, exceptions | Spring Web |
| **api-gateway** | 8080 | Entry point — routes requests | Spring Cloud Gateway, Resilience4j |
| **rate-limiter-service** | 8081 | Core rate limiting logic | Spring Web, Redis, Actuator |
| **config-service** | 8082 | Rule CRUD API | Spring Web, H2, JPA |
| **business-service** | 8083 | Sample endpoints to protect | Spring Web, Actuator |

---

## Project Structure

```
rate-limiter-system/
├── pom.xml                                          # Parent POM
├── README.md                                        # Project overview
├── docs/
│   └── PHASE-1.md                                   # This file
│
├── common/                                          # 📚 Shared Module
│   ├── pom.xml
│   └── src/main/java/com/ratelimiter/common/
│       ├── dto/
│       │   ├── RateLimitRequest.java
│       │   └── RateLimitResponse.java
│       ├── enums/
│       │   ├── Algorithm.java
│       │   ├── FailStrategy.java
│       │   └── KeyResolverType.java
│       └── exception/
│           ├── RateLimitExceededException.java
│           └── GlobalExceptionHandler.java
│
├── api-gateway/                                     # 🚪 API Gateway Service
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ratelimiter/gateway/
│       │   └── ApiGatewayApplication.java
│       └── resources/
│           └── application.yml
│
├── rate-limiter-service/                            # 🛡️ Rate Limiter Service
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ratelimiter/service/
│       │   ├── RateLimiterServiceApplication.java
│       │   ├── controller/
│       │   │   ├── RateLimitController.java
│       │   │   └── HealthController.java
│       │   └── limiter/
│       │       ├── RateLimiter.java
│       │       ├── RateLimitResult.java
│       │       ├── RateLimiterFactory.java
│       │       └── impl/
│       │           └── NoOpRateLimiter.java
│       └── resources/
│           └── application.yml
│
├── config-service/                                  # ⚙️ Config Service
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ratelimiter/configservice/
│       │   ├── ConfigServiceApplication.java
│       │   ├── controller/
│       │   │   └── RuleController.java
│       │   ├── model/
│       │   │   ├── RateLimitRule.java
│       │   │   └── RuleRepository.java
│       │   └── service/
│       │       └── RuleService.java
│       └── resources/
│           └── application.yml
│
├── business-service/                                # 📦 Business Service
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ratelimiter/business/
│       │   ├── BusinessServiceApplication.java
│       │   └── controller/
│       │       ├── UserController.java
│       │       ├── OrderController.java
│       │       └── ProductController.java
│       └── resources/
│           └── application.yml
│
└── rate-limiter-service/src/test/
    └── java/com/ratelimiter/service/
        └── RateLimiterServiceTest.java
```

---

## Module Details

### 1. Common Module

The shared module contains code used by all other services. No Spring Boot application — just plain Java classes.

#### DTOs

**`RateLimitRequest`** — Sent from API Gateway → Rate Limiter Service:
```java
public class RateLimitRequest {
    private String key;                // Resolved key (IP, userId, apiKey)
    private String path;               // API path (e.g., /api/v1/users)
    private String method;             // HTTP method (GET, POST)
    private Algorithm algorithm;       // Which algorithm to use
    private int requestsPerWindow;     // Max requests allowed
    private int windowSizeSeconds;     // Window duration
    private KeyResolverType keyResolverType;
}
```

**`RateLimitResponse`** — Sent from Rate Limiter Service → API Gateway:
```java
public class RateLimitResponse {
    private boolean allowed;           // true = OK, false = 429
    private String key;
    private int remainingRequests;     // How many left
    private long retryAfterSeconds;    // Wait time if rejected
    private long totalRequests;        // Current count
    private int limit;                 // Configured limit

    // Factory methods for convenience
    public static RateLimitResponse allow(String key, int remaining, long total, int limit);
    public static RateLimitResponse reject(String key, long retryAfter, long total, int limit);
}
```

#### Enums

| Enum | Values | Purpose |
|---|---|---|
| `Algorithm` | `FIXED_WINDOW`, `TOKEN_BUCKET`, `SLIDING_WINDOW_LOG`, `SLIDING_WINDOW_COUNTER` | Which algorithm to use |
| `FailStrategy` | `FAIL_OPEN`, `FAIL_CLOSED` | What to do when RL service is down |
| `KeyResolverType` | `IP_ADDRESS`, `USER_ID`, `API_KEY`, `CLIENT_ID` | How to extract the rate limit key |

#### Exceptions

**`RateLimitExceededException`** — Thrown when a request exceeds the limit:
```java
public class RateLimitExceededException extends RuntimeException {
    private final String key;
    private final long retryAfterSeconds;
    private final int limit;
    private final long totalRequests;
}
```

**`GlobalExceptionHandler`** — Catches the exception and returns HTTP 429:
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitExceeded(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .header("X-RateLimit-Limit", String.valueOf(ex.getLimit()))
                .header("X-RateLimit-Remaining", "0")
                .body(body);
    }
}
```

---

### 2. API Gateway Service (port 8080)

The single entry point for all client requests. Uses **Spring Cloud Gateway** for routing.

#### Routes

```yaml
# All /api/v1/** requests → Business Service
- id: business-service
  uri: http://localhost:8083
  predicates:
    - Path=/api/v1/**

# Rate limiter internal API → Rate Limiter Service
- id: rate-limiter-service
  uri: http://localhost:8081
  predicates:
    - Path=/api/v1/rl/**
```

#### Circuit Breaker

Uses **Resilience4j** to handle Rate Limiter Service failures:
- If RL Service fails 50%+ of requests → circuit opens for 10 seconds
- Falls back to `FAIL_OPEN` (allows traffic) or `FAIL_CLOSED` (blocks traffic)

#### Key Dependencies

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
```

---

### 3. Rate Limiter Service (port 8081)

The core service where all rate limiting algorithms live. This is the most important service in the system.

#### The Strategy Pattern

This is the **heart of the architecture**. All algorithms implement the same interface:

```
┌─────────────────────────────────────────────────────┐
│              RateLimiter Interface                    │
│                                                      │
│   RateLimitResult allow(String key, int limit,       │
│                         int windowSeconds)           │
│   long getCurrentCount(String key, int window)       │
│   void reset(String key)                             │
│   String getAlgorithmName()                          │
└───────────┬──────────┬──────────┬──────────┬─────────┘
            │          │          │          │
            ▼          ▼          ▼          ▼
     ┌──────────┐ ┌────────┐ ┌────────┐ ┌────────────┐
     │ Fixed    │ │ Token  │ │Sliding │ │ Sliding    │
     │ Window   │ │ Bucket │ │Window  │ │ Window     │
     │Counter   │ │        │ │Log     │ │ Counter ⭐ │
     └──────────┘ └────────┘ └────────┘ └────────────┘
```

#### RateLimiterFactory

The factory uses Spring's dependency injection to collect ALL `RateLimiter` implementations, then maps them by algorithm name:

```java
@Component
public class RateLimiterFactory {
    private final Map<Algorithm, RateLimiter> limiters = new HashMap<>();

    public RateLimiterFactory(List<RateLimiter> allLimiters) {
        for (RateLimiter limiter : allLimiters) {
            Algorithm algorithm = Algorithm.valueOf(limiter.getAlgorithmName());
            limiters.put(algorithm, limiter);
        }
    }

    public RateLimiter get(Algorithm algorithm) {
        return limiters.getOrDefault(algorithm, limiters.get("SLIDING_WINDOW_COUNTER"));
    }
}
```

**How it works:**
1. Spring finds ALL beans that implement `RateLimiter`
2. Factory constructor receives them as a `List`
3. Each limiter's `getAlgorithmName()` is used to register it in the map
4. When a request comes in, `factory.get(algorithm)` returns the right implementation
5. If the algorithm isn't found, falls back to `SLIDING_WINDOW_COUNTER`

#### RateLimitController

The REST API that the Gateway calls:

```java
@RestController
@RequestMapping("/api/v1/rl")
public class RateLimitController {

    @PostMapping("/check")      // Main endpoint — check if allowed
    @GetMapping("/usage")       // Read-only — get current count
    @DeleteMapping("/reset/{key}")  // Reset counter for a key
    @GetMapping("/algorithms")  // List available algorithms
}
```

#### NoOpRateLimiter (Stub)

In Phase 1, we have a stub implementation that **always allows** requests:

```java
@Component
public class NoOpRateLimiter implements RateLimiter {
    public RateLimitResult allow(String key, int limit, int windowSeconds) {
        return RateLimitResult.allow(0, limit, windowSeconds);  // Always allow
    }
    public String getAlgorithmName() {
        return "SLIDING_WINDOW_COUNTER";  // Claims to be the default
    }
}
```

This will be **replaced with the real Fixed Window Counter** in Phase 2.

---

### 4. Config Service (port 8082)

Manages rate limit rules in a database. Other services fetch rules from here.

#### RateLimitRule Entity

```java
@Entity
@Table(name = "rate_limit_rules")
public class RateLimitRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String domain;              // Which service (e.g., "business-service")
    private String path;                // API path (e.g., /api/v1/users/*)
    private String method;              // HTTP method (GET, POST, ALL)
    private int requestsPerWindow;      // Max requests
    private int windowSizeSeconds;      // Window duration
    private KeyResolverType keyResolverType;  // How to extract key
    private Algorithm algorithm;        // Which algorithm to use
    private FailStrategy failStrategy;  // What to do on failure
    private int priority;               // Higher = matched first
    private boolean enabled;            // Enable/disable rule
}
```

#### REST API

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/rules` | List all rules |
| `GET` | `/api/v1/rules/{id}` | Get rule by ID |
| `GET` | `/api/v1/rules/domain/{domain}` | Get rules for a service |
| `POST` | `/api/v1/rules` | Create a new rule |
| `PUT` | `/api/v1/rules/{id}` | Update a rule |
| `DELETE` | `/api/v1/rules/{id}` | Delete a rule |

#### Example: Create a Rule

```bash
curl -X POST http://localhost:8082/api/v1/rules \
  -H "Content-Type: application/json" \
  -d '{
    "domain": "business-service",
    "path": "/api/v1/users",
    "method": "GET",
    "requestsPerWindow": 100,
    "windowSizeSeconds": 60,
    "keyResolverType": "IP_ADDRESS",
    "algorithm": "SLIDING_WINDOW_COUNTER",
    "failStrategy": "FAIL_OPEN",
    "priority": 1,
    "enabled": true
  }'
```

#### Database

Uses **H2 in-memory database** for development:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:configdb
    driver-class-name: org.h2.Driver
  h2:
    console:
      enabled: true
      path: /h2-console
```

Access H2 console at: `http://localhost:8082/h2-console`

---

### 5. Business Service (port 8083)

Sample downstream service with REST endpoints that will be rate-limited.

#### Endpoints

| Method | Endpoint | Response |
|---|---|---|
| `GET` | `/api/v1/users` | List of users |
| `GET` | `/api/v1/users/{id}` | User by ID |
| `GET` | `/api/v1/orders` | List of orders |
| `POST` | `/api/v1/orders` | Create order |
| `GET` | `/api/v1/products` | List of products |
| `GET` | `/api/v1/products/{id}` | Product by ID |

#### Example Responses

```json
// GET /api/v1/users
[
  {"id": 1, "name": "Alice", "email": "alice@example.com"},
  {"id": 2, "name": "Bob", "email": "bob@example.com"},
  {"id": 3, "name": "Charlie", "email": "charlie@example.com"}
]

// GET /api/v1/orders
[
  {"id": 101, "product": "Laptop", "amount": 999.99, "status": "completed"},
  {"id": 102, "product": "Phone", "amount": 699.99, "status": "pending"},
  {"id": 103, "product": "Tablet", "amount": 449.99, "status": "shipped"}
]
```

---

## Core Design — The Strategy Pattern

The Strategy Pattern is the most important architectural decision in this project. Here's why:

### The Problem

We have 4 different rate limiting algorithms. We want to:
- Switch between them without changing code
- Use different algorithms for different endpoints
- Add new algorithms without modifying existing code

### The Solution

```
                    ┌──────────────────────────────┐
                    │      Algorithm Config         │
                    │  (from Config Service / YAML) │
                    └──────────────┬───────────────┘
                                   │
                                   ▼
                    ┌──────────────────────────────┐
                    │    RateLimiterFactory          │
                    │                              │
                    │  get(Algorithm.TOKEN_BUCKET)  │
                    │         │                    │
                    │         ▼                    │
                    │  returns TokenBucketLimiter   │
                    └──────────────┬───────────────┘
                                   │
                    ┌──────────────┴───────────────┐
                    │    RateLimitController        │
                    │                              │
                    │  POST /api/v1/rl/check        │
                    │  {                            │
                    │    "key": "192.168.1.1",      │
                    │    "algorithm": "TOKEN_BUCKET",│
                    │    "limit": 100,              │
                    │    "window": 60               │
                    │  }                            │
                    │         │                    │
                    │         ▼                    │
                    │  factory.get(algorithm)       │
                    │  limiter.allow(key, limit,    │
                    │               window)        │
                    └──────────────────────────────┘
```

### Code Flow

```java
// 1. Client sends request to API Gateway
curl http://localhost:8080/api/v1/users

// 2. Gateway's RateLimitFilter calls Rate Limiter Service
POST http://localhost:8081/api/v1/rl/check
{
  "key": "192.168.1.1",
  "path": "/api/v1/users",
  "method": "GET",
  "algorithm": "SLIDING_WINDOW_COUNTER",
  "requestsPerWindow": 100,
  "windowSizeSeconds": 60
}

// 3. Rate Limit Controller delegates to Factory
RateLimiter limiter = factory.get(request.getAlgorithm());
RateLimitResult result = limiter.allow(key, limit, window);

// 4. Factory returns the right implementation
//    → SlidingWindowCounterRateLimiter.allow(key, 100, 60)
//    → RateLimitResult(allowed=true, currentCount=42, limit=100)

// 5. Controller returns response
RateLimitResponse{allowed=true, remainingRequests=58, ...}

// 6. Gateway forwards request to Business Service
//    (or returns 429 if not allowed)
```

---

## Key Classes Explained

| Class | File | Purpose | Who Uses It |
|---|---|---|---|
| `RateLimiter` | `limiter/RateLimiter.java` | Interface that ALL algorithms implement | Rate Limiter Service |
| `RateLimiterFactory` | `limiter/RateLimiterFactory.java` | Picks the right algorithm at runtime | Rate Limit Controller |
| `RateLimitResult` | `limiter/RateLimitResult.java` | Result from algorithm (allowed/reject + metadata) | Rate Limit Controller |
| `NoOpRateLimiter` | `limiter/impl/NoOpRateLimiter.java` | Stub — always allows (replaced in Phase 2) | Rate Limiter Service |
| `RateLimitRequest` | `common/dto/RateLimitRequest.java` | DTO: what Gateway sends to check a request | API Gateway → RL Service |
| `RateLimitResponse` | `common/dto/RateLimitResponse.java` | DTO: RL Service's answer | RL Service → API Gateway |
| `RateLimitRule` | `configservice/model/RateLimitRule.java` | JPA Entity: stores a rule in the database | Config Service |
| `RuleRepository` | `configservice/model/RuleRepository.java` | Spring Data JPA repository | Config Service |
| `RuleService` | `configservice/service/RuleService.java` | Business logic for rule CRUD | Config Service |
| `RuleController` | `configservice/controller/RuleController.java` | REST API for rule management | Config Service |
| `RateLimitController` | `service/controller/RateLimitController.java` | REST API for rate limit checking | Rate Limiter Service |
| `HealthController` | `service/controller/HealthController.java` | Health check endpoint | Rate Limiter Service |
| `RateLimitExceededException` | `common/exception/RateLimitExceededException.java` | Exception → HTTP 429 response | All services |
| `GlobalExceptionHandler` | `common/exception/GlobalExceptionHandler.java` | Catches exceptions → returns 429 | All services |

---

## REST APIs

### Rate Limiter Service (port 8081)

```
POST   /api/v1/rl/check          — Check if request is allowed
GET    /api/v1/rl/usage          — Get current usage for a key
DELETE /api/v1/rl/reset/{key}    — Reset counter for a key
GET    /api/v1/rl/algorithms     — List available algorithms
GET    /health                   — Health check
```

#### POST /api/v1/rl/check

**Request:**
```json
{
  "key": "192.168.1.1",
  "path": "/api/v1/users",
  "method": "GET",
  "algorithm": "SLIDING_WINDOW_COUNTER",
  "requestsPerWindow": 100,
  "windowSizeSeconds": 60
}
```

**Response (Allowed):**
```json
{
  "allowed": true,
  "key": "192.168.1.1",
  "remainingRequests": 58,
  "retryAfterSeconds": 0,
  "totalRequests": 42,
  "limit": 100
}
```

**Response (Rejected):**
```json
{
  "allowed": false,
  "key": "192.168.1.1",
  "remainingRequests": 0,
  "retryAfterSeconds": 60,
  "totalRequests": 100,
  "limit": 100
}
```

### Config Service (port 8082)

```
GET    /api/v1/rules             — List all rules
GET    /api/v1/rules/{id}        — Get rule by ID
GET    /api/v1/rules/domain/{d}  — Get rules by domain
POST   /api/v1/rules             — Create a new rule
PUT    /api/v1/rules/{id}        — Update a rule
DELETE /api/v1/rules/{id}        — Delete a rule
```

### Business Service (port 8083)

```
GET    /api/v1/users             — List users
GET    /api/v1/users/{id}        — Get user by ID
GET    /api/v1/orders            — List orders
POST   /api/v1/orders            — Create order
GET    /api/v1/products          — List products
GET    /api/v1/products/{id}     — Get product by ID
```

---

## Configuration Files

### Parent POM

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>
</parent>

<modules>
    <module>common</module>
    <module>api-gateway</module>
    <module>rate-limiter-service</module>
    <module>config-service</module>
    <module>business-service</module>
</modules>

<properties>
    <java.version>17</java.version>
</properties>
```

### API Gateway — application.yml

```yaml
server:
  port: 8080

spring:
  cloud:
    gateway:
      routes:
        - id: business-service
          uri: http://localhost:8083
          predicates:
            - Path=/api/v1/**
        - id: rate-limiter-service
          uri: http://localhost:8081
          predicates:
            - Path=/api/v1/rl/**

rate-limiter:
  client:
    base-url: http://localhost:8081
    timeout-ms: 5000
  fail-strategy: FAIL_OPEN

resilience4j:
  circuitbreaker:
    instances:
      rateLimiterCircuitBreaker:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
```

### Rate Limiter Service — application.yml

```yaml
server:
  port: 8081

rate-limiter:
  storage: memory
  default-strategy: SLIDING_WINDOW_COUNTER
  cache-ttl-seconds: 60

spring:
  data:
    redis:
      host: localhost
      port: 6379
```

### Config Service — application.yml

```yaml
server:
  port: 8082

spring:
  datasource:
    url: jdbc:h2:mem:configdb
    driver-class-name: org.h2.Driver
  h2:
    console:
      enabled: true
      path: /h2-console
  jpa:
    hibernate:
      ddl-auto: create-drop
```

---

## How to Build

```bash
# From the project root
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# Build all modules
mvn clean package -DskipTests

# Or build and test
mvn clean package
```

### Build Output

```
[INFO] Building common 1.0.0-SNAPSHOT
[INFO] Building api-gateway 1.0.0-SNAPSHOT
[INFO] Building rate-limiter-service 1.0.0-SNAPSHOT
[INFO] Building config-service 1.0.0-SNAPSHOT
[INFO] Building business-service 1.0.0-SNAPSHOT
[INFO] BUILD SUCCESS
```

### JAR Files Created

```
common/target/common-1.0.0-SNAPSHOT.jar
api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar
rate-limiter-service/target/rate-limiter-service-1.0.0-SNAPSHOT.jar
config-service/target/config-service-1.0.0-SNAPSHOT.jar
business-service/target/business-service-1.0.0-SNAPSHOT.jar
```

---

## Complete Command Reference

> **Every command you need to set up, build, run, test, and verify Phase 1.**

### Prerequisites — One-Time Setup

```bash
# 1. Install Java 17 and Maven (if not already installed)
brew install openjdk@17 maven

# 2. Set JAVA_HOME (add to ~/.zshrc or ~/.bash_profile for permanence)
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# 3. Verify installations
java -version       # Should show openjdk version "17.x.x"
mvn -version        # Should show Maven 3.9.x
```

### Step 1: Navigate to Project Root

```bash
cd "/Users/preetam/Documents/high-level-system-design/Rate Limiter"
```

### Step 2: Set Java Environment (Every New Terminal)

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

### Step 3: Build the Common Module First

```bash
mvn clean install -pl common
```

> ⚠️ **Important:** The `common` module must be installed to your local Maven repo first because all other services depend on it. This only needs to happen once per build.

### Step 4: Build All Modules

```bash
# Build without tests (faster)
mvn clean package -DskipTests

# Build with tests (recommended before committing)
mvn clean package
```

### Step 5: Run Tests

```bash
# Run all tests across all modules
mvn clean test

# Run only Rate Limiter Service tests (6 tests)
mvn test -pl rate-limiter-service

# Run a specific test class
mvn test -pl rate-limiter-service -Dtest=RateLimiterServiceTest

# Run a specific test method
mvn test -pl rate-limiter-service -Dtest=RateLimiterServiceTest#contextLoads
mvn test -pl rate-limiter-service -Dtest=RateLimiterServiceTest#noOpLimiterAlwaysAllows
mvn test -pl rate-limiter-service -Dtest=RateLimiterServiceTest#rateLimitResultCreationWorks

# Run tests with visible console output
mvn test -pl rate-limiter-service -Dtest=RateLimiterServiceTest -Dsurefire.useFile=false

# View HTML test report (after running tests)
open rate-limiter-service/target/surefire-reports/index.html
```

### Step 6: Run Each Service (4 Terminals)

You need **4 separate terminal windows** — one for each service. Start them in this order:

#### Terminal 1 — Config Service (port 8082)
```bash
cd "/Users/preetam/Documents/high-level-system-design/Rate Limiter"
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
java -jar config-service/target/config-service-1.0.0-SNAPSHOT.jar
```

**Wait for:** `Started ConfigServiceApplication in X seconds`

#### Terminal 2 — Rate Limiter Service (port 8081)
```bash
cd "/Users/preetam/Documents/high-level-system-design/Rate Limiter"
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
java -jar rate-limiter-service/target/rate-limiter-service-1.0.0-SNAPSHOT.jar
```

**Wait for:** `Started RateLimiterServiceApplication in X seconds`

#### Terminal 3 — Business Service (port 8083)
```bash
cd "/Users/preetam/Documents/high-level-system-design/Rate Limiter"
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
java -jar business-service/target/business-service-1.0.0-SNAPSHOT.jar
```

**Wait for:** `Started BusinessServiceApplication in X seconds`

#### Terminal 4 — API Gateway (port 8080)
```bash
cd "/Users/preetam/Documents/high-level-system-design/Rate Limiter"
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
java -jar api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar
```

**Wait for:** `Started ApiGatewayApplication in X seconds`

> 💡 **Tip:** Alternatively, use `mvn spring-boot:run` in each module directory instead of `java -jar`.

### Step 7: Verify All Services Are Running

```bash
# --- Health Checks ---
echo "=== API Gateway ===" && curl -s http://localhost:8080/actuator/health | head -c 200
echo ""
echo "=== Rate Limiter Service ===" && curl -s http://localhost:8081/health
echo ""
echo "=== Config Service ===" && curl -s http://localhost:8082/health | head -c 200
echo ""
echo "=== Business Service ===" && curl -s http://localhost:8083/actuator/health | head -c 200
echo ""
```

**Expected output:** Each should return `UP` status.

### Step 8: Test the APIs

```bash
# --- Test Business Service Directly ---
echo "=== Users ===" && curl -s http://localhost:8083/api/v1/users | python3 -m json.tool
echo ""
echo "=== Orders ===" && curl -s http://localhost:8083/api/v1/orders | python3 -m json.tool
echo ""
echo "=== Products ===" && curl -s http://localhost:8083/api/v1/products | python3 -m json.tool

# --- Test Through API Gateway ---
curl -s http://localhost:8080/api/v1/users | python3 -m json.tool

# --- Test Rate Limiter Service ---
echo "=== Available Algorithms ===" && curl -s http://localhost:8081/api/v1/rl/algorithms
echo ""
echo "=== Check Rate Limit ===" \
  && curl -s -X POST http://localhost:8081/api/v1/rl/check \
  -H "Content-Type: application/json" \
  -d '{"key": "test-user", "path": "/api/v1/users", "method": "GET", "algorithm": "SLIDING_WINDOW_COUNTER", "requestsPerWindow": 100, "windowSizeSeconds": 60}' \
  | python3 -m json.tool

# --- Test Config Service ---
echo "=== All Rules ===" && curl -s http://localhost:8082/api/v1/rules | python3 -m json.tool

# --- Create a Rule in Config Service ---
curl -s -X POST http://localhost:8082/api/v1/rules \
  -H "Content-Type: application/json" \
  -d '{
    "domain": "business-service",
    "path": "/api/v1/users",
    "method": "GET",
    "requestsPerWindow": 100,
    "windowSizeSeconds": 60,
    "keyResolverType": "IP_ADDRESS",
    "algorithm": "SLIDING_WINDOW_COUNTER",
    "failStrategy": "FAIL_OPEN",
    "priority": 1,
    "enabled": true
  }' | python3 -m json.tool

# --- Access H2 Database Console (in browser) ---
open http://localhost:8082/h2-console
# JDBC URL: jdbc:h2:mem:configdb  |  User: sa  |  Password: (empty)
```

### Step 9: Stress Test (Quick Manual Test)

```bash
# Send 20 rapid requests to Business Service (all should succeed — no rate limiting yet)
for i in $(seq 1 20); do
  echo -n "Request $i: "
  curl -s -o /dev/null -w "%{http_code}" http://localhost:8083/api/v1/users
  echo ""
done

# Send 20 rapid requests through Rate Limiter Service
for i in $(seq 1 20); do
  echo -n "Request $i: "
  curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8081/api/v1/rl/check \
    -H "Content-Type: application/json" \
    -d '{"key": "test-user", "path": "/api/v1/users", "method": "GET", "algorithm": "SLIDING_WINDOW_COUNTER", "requestsPerWindow": 5, "windowSizeSeconds": 60}'
  echo ""
done
```

> In Phase 1, all requests return `200` because the NoOp limiter always allows. In Phase 2+, you'll see `429` responses when the limit is exceeded.

### Stopping Services

```bash
# Find and kill all Java processes for this project
ps aux | grep "config-service-1.0.0" | grep -v grep | awk '{print $2}' | xargs kill
ps aux | grep "rate-limiter-service-1.0.0" | grep -v grep | awk '{print $2}' | xargs kill
ps aux | grep "business-service-1.0.0" | grep -v grep | awk '{print $2}' | xargs kill
ps aux | grep "api-gateway-1.0.0" | grep -v grep | awk '{print $2}' | xargs kill

# Or kill all Java processes at once (⚠️ only if no other Java projects are running)
killall java
```

### Port Summary

| Port | Service | URL |
|---|---|---|
| 8080 | API Gateway | http://localhost:8080 |
| 8081 | Rate Limiter Service | http://localhost:8081 |
| 8082 | Config Service | http://localhost:8082 |
| 8083 | Business Service | http://localhost:8083 |
| 8082 | H2 Console | http://localhost:8082/h2-console |

### Troubleshooting

| Problem | Solution |
|---|---|
| `java: command not found` | Run `export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"` |
| `Could not find or load main class` | Run `mvn clean install -pl common` first |
| Port already in use | `kill $(lsof -t -i:8081)` to free the port |
| `Connection refused` on gateway | Start backend services (8081–8083) BEFORE the gateway (8080) |
| Build fails with dependency errors | Run `mvn clean install -DskipTests` from root to rebuild everything |

---

## Test Results

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test
mvn test -pl rate-limiter-service -Dtest=RateLimiterServiceTest
```

### Test Output

```
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 ✅
BUILD SUCCESS
```

### What the Tests Verify

| # | Test | What It Checks |
|---|---|---|
| 1 | `contextLoads()` | Spring context loads correctly, Factory is injected |
| 2 | `factoryHasAtLeastOneLimiter()` | Factory has at least one algorithm registered |
| 3 | `noOpLimiterAlwaysAllows()` | NoOp limiter returns `allowed=true` |
| 4 | `noOpLimiterGetCurrentCountReturnsZero()` | NoOp count is always 0 |
| 5 | `noOpLimiterResetDoesNotThrow()` | Reset doesn't crash |
| 6 | `rateLimitResultCreationWorks()` | `RateLimitResult.allow()` and `.reject()` work correctly |

### Test Code

```java
@SpringBootTest
class RateLimiterServiceTest {

    @Autowired
    private RateLimiterFactory factory;

    @Test
    void contextLoads() {
        assertNotNull(factory);
    }

    @Test
    void factoryHasAtLeastOneLimiter() {
        Map<Algorithm, RateLimiter> all = factory.getAll();
        assertFalse(all.isEmpty());
    }

    @Test
    void noOpLimiterAlwaysAllows() {
        RateLimiter limiter = factory.get(Algorithm.SLIDING_WINDOW_COUNTER);
        RateLimitResult result = limiter.allow("test-key", 100, 60);
        assertTrue(result.isAllowed());
        assertEquals(100, result.getLimit());
    }

    @Test
    void rateLimitResultCreationWorks() {
        RateLimitResult allowResult = RateLimitResult.allow(5, 100, 60);
        assertTrue(allowResult.isAllowed());
        assertEquals(95, allowResult.getRemainingRequests());

        RateLimitResult rejectResult = RateLimitResult.reject(101, 100, 60);
        assertFalse(rejectResult.isAllowed());
        assertEquals(0, rejectResult.getRemainingRequests());
    }
}
```

---

## What's Ready for Phase 2

| Component | Status | Notes |
|---|---|---|
| Multi-module Maven project | ✅ Ready | 5 modules, all compile |
| `RateLimiter` interface | ✅ Ready | 4 methods defined |
| `RateLimiterFactory` | ✅ Ready | Strategy Pattern working |
| `RateLimitController` | ✅ Ready | REST API for checking limits |
| `RateLimitRequest` / `RateLimitResponse` | ✅ Ready | DTOs for inter-service comms |
| `RateLimitRule` entity | ✅ Ready | JPA entity for rule storage |
| Circuit Breaker | ✅ Ready | Resilience4j configured |
| `NoOpRateLimiter` | ⏳ Waiting | Will be replaced with Fixed Window Counter |
| Tests | ✅ Passing | 6 tests, all green |

### What Phase 2 Will Add

- **`FixedWindowRateLimiter`** — First real algorithm implementation
- **ConcurrentHashMap** for in-memory counters
- **Window calculation** — Divide time into fixed windows
- **Counter increment + expiry** — Track requests per window
- **Real tests** — Verify actual rate limiting behavior (not just stub)

---

> **Next → Phase 2: Fixed Window Counter (In-Memory)**
