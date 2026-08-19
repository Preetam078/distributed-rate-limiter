# 🔒 Rate Limiter — Microservices System Design Journey

> Building a **production-grade, distributed Rate Limiter** as a set of **proper microservices** using **Java Spring Boot**, following the architecture patterns from **Alex Xu's System Design Interview — Chapter 4**.

---

## 📚 Table of Contents

1. [What We're Building](#what-were-building)
2. [Why Rate Limiting?](#why-rate-limiting)
3. [The Journey — Phase by Phase](#the-journey--phase-by-phase)
4. [Rate Limiting Algorithms](#rate-limiting-algorithms)
5. [Microservices Architecture](#microservices-architecture)
6. [Service Descriptions](#service-descriptions)
7. [Inter-Service Communication](#inter-service-communication)
8. [Project Structure — All Services](#project-structure--all-services)
9. [Tech Stack](#tech-stack)
10. [How to Run](#how-to-run)

---

## What We're Building

A **distributed Rate Limiter** implemented as **independent microservices** — not a single monolith. Each service is independently deployable, scalable, and replaceable. This mirrors how companies like **Stripe, Cloudflare, Shopify, and Netflix** handle rate limiting at scale.

### The Big Picture — An Analogy

Imagine you own a restaurant. Without a bouncer, anyone can walk in — 1 person, 100 people, a mob. Your kitchen collapses. A **rate limiter** is that bouncer — it controls **how many requests each client can make** within a time window.

We're building this **bouncer as a set of microservices** — not just a single class, but a **real distributed system** like what Stripe, Cloudflare, or Netflix use.

#### The Problem We're Solving

```
Without Rate Limiting:
┌────────┐     ┌─────────────────────────────────┐
│ Client  │────→│                                 │
│ (bot)   │────→│       Your Server               │────→ 💥 CRASH
│ (bot)   │────→│   (overwhelmed by traffic)      │
│ (bot)   │────→│                                 │
└────────┘     └─────────────────────────────────┘

With Rate Limiting:
┌────────┐     ┌──────────────────┐     ┌─────────────┐
│ Client  │────→│  Rate Limiter   │────→│ Your Server │────→ ✅ Works
│ (10/s)  │     │  "Max 5/sec"    │     │ (protected) │
└────────┘     │  ✅ ALLOW        │     └─────────────┘
┌────────┐     │  ✅ ALLOW        │
│ Client  │────→│  ✅ ALLOW        │
│ (bot)   │     │  ✅ ALLOW        │
│ (bot)   │     │  ✅ ALLOW        │
│ (bot)   │     │  ❌ REJECT 429   │ ← blocked!
│ (bot)   │     │  ❌ REJECT 429   │ ← blocked!
└────────┘     └──────────────────┘
```

#### What Does Each Microservice Do?

Think of it like a real company with different departments:

```
Client (your phone/browser)
    │
    ▼
🚪 FRONT DESK (API Gateway)
    "Welcome! Let me check if you're allowed in..."
    │
    ├──→ 🛡️ SECURITY DESK (Rate Limiter Service)
    │        "Does this client have quota left?"
    │        "Yes? ✅ Let them through"
    │        "No? ❌ Reject with 429"
    │
    ├──→ ⚙️ HR DEPARTMENT (Config Service)
    │        "Here are the rules: User A gets 100 req/min,
    │         User B gets 50 req/min, Free tier gets 10/min"
    │
    └──→ 📦 KITCHEN (Business Service)
             "The actual work — serve the food (API response)"
```

| Service | Analogy | What It Actually Does |
|---|---|---|
| 🚪 **API Gateway** | Front desk | Receives ALL traffic, decides where to route it |
| 🛡️ **Rate Limiter** | Security guard | Checks "Is this client allowed? How many requests left?" |
| ⚙️ **Config Service** | HR policy book | Stores rules like "User A: 100 req/min" |
| 📦 **Business Service** | Kitchen | Does the actual work (fetch users, process orders) |
| 🔴 **Redis** | Shared scoreboard | Stores live counters that ALL servers can see |

#### Why Microservices (Not One Big App)?

```
❌ Monolith (one app does everything):
┌─────────────────────────────────┐
│           ONE HUGE APP          │
│  Gateway + Limiter + Config +   │
│  Business Logic + Everything    │
│                                 │
│  Problem: Can't scale           │
│  separately. One bug crashes    │
│  EVERYTHING.                    │
└─────────────────────────────────┘

✅ Microservices (what we're building):
┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
│ Gateway  │  │ Limiter  │  │ Config   │  │ Business │
│ (scale   │  │ (scale   │  │ (scale   │  │ (scale   │
│  when    │  │  when    │  │  when    │  │  when    │
│  needed) │  │  needed) │  │  needed) │  │  needed) │
└──────────┘  └──────────┘  └──────────┘  └──────────┘
    Deploy      Deploy        Deploy        Deploy
   independently independently independently independently
```

### Our Microservices:

| Service | Port | Responsibility |
|---|---|---|
| 🚪 **API Gateway** | 8080 | Entry point — routes requests, applies rate limiting |
| 🛡️ **Rate Limiter Service** | 8081 | Core rate limiting logic — all algorithms live here |
| ⚙️ **Config Service** | 8082 | Centralized rate limit rules (YAML-backed, REST API) |
| 📦 **Business Service** | 8083 | Sample downstream service to protect |
| 🎯 **Traffic Simulator** | 8084 | Mimics real users — low QPS to high QPS traffic patterns |
| 🔴 **Redis** | 6379 | Shared state for distributed counters |
| 📊 **Monitoring** | 9090 | Prometheus + Grafana dashboards |

---

## Why Rate Limiting?

| Threat | What Rate Limiting Prevents |
|---|---|
| **DoS / DDoS Attacks** | Block flood of malicious requests |
| **Abuse & Spam** | Prevent bots from hammering endpoints |
| **Cost Control** | Limit compute resource consumption per user |
| **Fair Usage** | Ensure equitable access across all clients |
| **Revenue Protection** | Enforce API quota limits for paid tiers |

---

## 🔄 Pluggable Algorithm Design — Switch Anytime

> **Key Principle:** You can switch rate limiting algorithms **at runtime** without changing a single line of code in the API Gateway, Config Service, or Business Service.

### How It Works

All 4 algorithms implement the **same `RateLimiter` interface**. The Rate Limiter Service uses a **Strategy Pattern** — it looks at the rule's `strategy` field and picks the right algorithm on the fly.

```
┌─────────────────────────────────────────────────────┐
│              RateLimiter Interface                    │
│                                                      │
│   boolean allow(String key, RateLimitRule rule)      │
│   RateLimitResult check(String key, RateLimitRule)   │
└───────────┬──────────┬──────────┬──────────┬─────────┘
            │          │          │          │
            ▼          ▼          ▼          ▼
     ┌──────────┐ ┌────────┐ ┌────────┐ ┌────────────┐
     │ Fixed    │ │ Token  │ │Sliding │ │ Sliding    │
     │ Window   │ │ Bucket │ │Window  │ │ Window     │
     │Counter   │ │        │ │Log     │ │ Counter ⭐ │
     └──────────┘ └────────┘ └────────┘ └────────────┘
```

### Switching via Config (Zero Code Changes)

```json
// Rule A: Use Token Bucket for payment endpoints
{
  "path": "/api/v1/payments/*",
  "strategy": "TOKEN_BUCKET",
  "bucketCapacity": 10,
  "refillRate": 2
}

// Rule B: Use Sliding Window Counter for user endpoints
{
  "path": "/api/v1/users/*",
  "strategy": "SLIDING_WINDOW_COUNTER",
  "requestsPerWindow": 100,
  "windowSizeSeconds": 60
}

// Rule C: Change your mind later? Just update the rule.
// No redeployment. No code change. Instant switch.
{
  "path": "/api/v1/users/*",
  "strategy": "FIXED_WINDOW",    ← changed from SLIDING_WINDOW_COUNTER
  "requestsPerWindow": 50,
  "windowSizeSeconds": 60
}
```

### What Stays the Same (Regardless of Algorithm)

| Component | Never Changes |
|---|---|
| 🚪 API Gateway | Always calls `POST /api/v1/check` — doesn't know or care which algorithm is used |
| 📦 Business Service | Always uses `@RateLimit` annotation — algorithm is a config detail |
| 🔴 Redis | Always stores counters — format may differ but connection is the same |
| ⚙️ Config Service | Always stores rules — `strategy` is just another field |

### What Changes (Only Inside Rate Limiter Service)

| When You Switch | What Happens Inside |
|---|---|
| `strategy` field updated in Config Service | Rate Limiter Service picks up the change (polling or webhook) |
| New request arrives | `AlgorithmFactory.get(strategy)` returns the right implementation |
| Lua script | The correct `.lua` file is loaded and executed |
| Counter format | May change (e.g., simple counter vs sorted set) — but only in Redis |

### The Code Pattern

```java
// RateLimiter interface — ALL algorithms implement this
public interface RateLimiter {
    RateLimitResult allow(String key, RateLimitRule rule);
}

// Factory — picks the right algorithm at runtime
@Component
public class RateLimiterFactory {
    private final Map<String, RateLimiter> limiters;

    public RateLimiterFactory(
        FixedWindowRateLimiter fixedWindow,
        TokenBucketRateLimiter tokenBucket,
        SlidingWindowLogRateLimiter slidingLog,
        SlidingWindowCounterRateLimiter slidingCounter
    ) {
        this.limiters = Map.of(
            "FIXED_WINDOW", fixedWindow,
            "TOKEN_BUCKET", tokenBucket,
            "SLIDING_WINDOW_LOG", slidingLog,
            "SLIDING_WINDOW_COUNTER", slidingCounter
        );
    }

    public RateLimiter get(String strategy) {
        return limiters.getOrDefault(strategy, limiters.get("SLIDING_WINDOW_COUNTER"));
    }
}

// Service — delegates to the factory
@Service
public class RateLimitService {
    private final RateLimiterFactory factory;
    private final RuleFetchService ruleService;

    public RateLimitResult check(String key, String path) {
        RateLimitRule rule = ruleService.getRule(path);
        RateLimiter limiter = factory.get(rule.getStrategy());  // ← dynamic!
        return limiter.allow(key, rule);
    }
}
```

### Supported Algorithms at a Glance

| Algorithm | When to Use | Switch By Setting `strategy` to |
|---|---|---|
| **Fixed Window** | Simple prototypes, low-stakes endpoints | `FIXED_WINDOW` |
| **Token Bucket** | APIs that allow bursts (e.g., file uploads) | `TOKEN_BUCKET` |
| **Sliding Window Log** | High-accuracy needs (e.g., billing) | `SLIDING_WINDOW_LOG` |
| **Sliding Window Counter** ⭐ | Production default — best balance | `SLIDING_WINDOW_COUNTER` |

> **Bottom line:** You can run Token Bucket on `/payments` and Sliding Window Counter on `/users` simultaneously — different rules, different algorithms, same system, zero code changes.

---

## The Journey — Phase by Phase

### Phase 1: 🏗️ Multi-Module Maven Project Setup
> **Goal:** Create a proper multi-module Maven project with all microservices.

- Parent `pom.xml` with shared dependencies
- Each service as an independent Maven module
- `docker-compose.yml` for local development (all services + Redis)
- Service discovery with **Eureka** or **Consul** (or simple REST calls initially)

```
rate-limiter-system/
├── pom.xml                          # Parent POM
├── docker-compose.yml               # All services + Redis
├── api-gateway/                     # Module 1
├── rate-limiter-service/            # Module 2
├── config-service/                  # Module 3
├── business-service/                # Module 4
└── common/                          # Shared DTOs, exceptions, utils
```

**Deliverable:** All 4 services start independently, communicate via REST.

---

### Phase 2: 🪟 Fixed Window Counter (In-Memory) — Inside Rate Limiter Service
> **Goal:** Implement the simplest algorithm inside the dedicated Rate Limiter microservice.

**Algorithm:**
```
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ Window 1        │  │ Window 2        │  │ Window 3        │
│ [00:00 - 01:00] │  │ [01:00 - 02:00] │  │ [02:00 - 03:00] │
│ Count: 47/100   │  │ Count: 23/100   │  │ Count: 0/100    │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

- Rate Limiter Service exposes: `POST /api/v1/check` — returns `ALLOW` or `REJECT`
- API Gateway calls Rate Limiter Service on every incoming request
- In-memory `ConcurrentHashMap` for counters (single-instance only for now)
- **Known flaw:** Boundary problem — 2x burst at window edges

**Deliverable:** API Gateway → Rate Limiter Service call works. Fixed Window in-memory.

---

### Phase 3: 🪣 Token Bucket (In-Memory)
> **Goal:** Implement burst-friendly rate limiting.

**Algorithm:**
```
    Refill Rate: 2 tokens/sec
         ↓   ↓
    ┌─────────────┐
    │ 🪙 🪙 🪙    │  ← Bucket Capacity: 10
    │ 🪙 🪙       │  
    │             │  Request → Consume 1 token
    │             │  Empty? → REJECT (429)
    └─────────────┘
```

- Bucket holds tokens up to `maxCapacity`
- Tokens refilled at `refillRate` per second
- Each request consumes 1 token; if empty → reject
- **Allows bursts** up to bucket capacity, then throttles smoothly

**Deliverable:** Token Bucket implemented in Rate Limiter Service. Switchable via API.

---

### Phase 4: 📜 Sliding Window Log (In-Memory)
> **Goal:** Build an accurate but memory-heavy rate limiter.

**Algorithm:**
```
Sliding Window (last 60 seconds from now)
┌──────────────────────────────────────────┐
│ Request Log (sorted set of timestamps):  │
│ [10:00:01, 10:00:15, 10:00:22, ...]     │
│                                          │
│ Remove entries older than (now - 60s)    │
│ Count remaining → if > limit → REJECT    │
└──────────────────────────────────────────┘
```

- Store timestamp of every request in a sorted structure
- On each new request: purge old entries, count remaining
- **100% accurate** — no boundary problem
- **Downside:** High memory — stores every single request timestamp

**Deliverable:** Sliding Window Log in Rate Limiter Service.

---

### Phase 5: ⚖️ Sliding Window Counter (Hybrid) ⭐
> **Goal:** The production-favorite — accurate + memory-efficient.

**Algorithm:**
```
Previous Window        Current Window
┌───────────────┐      ┌───────────────┐
│ Count: 80     │      │ Count: 30     │
│ Weight: 0.3   │      │ Weight: 0.7   │
└───────────────┘      └───────────────┘

Estimated = 80 × 0.3 + 30 × 0.7 = 45 requests
If 45 > limit → REJECT
```

- Combine Fixed Window + Sliding Window Log concepts
- Estimate using weighted sum of current + previous window
- **Best balance** of accuracy, memory, and simplicity
- **This is the recommended default for production systems**

**Deliverable:** All 4 algorithms implemented in Rate Limiter Service, selectable via API parameter.

---

### Phase 6: 🏷️ Rate Limiter as a Library + Filter
> **Goal:** Package the rate limiter logic as a reusable Spring Boot starter.

```java
// Any downstream service can just add this dependency and annotate:
@RateLimit(key = "userId", limit = 100, window = 60, unit = TimeUnit.SECONDS)
@GetMapping("/api/messages")
public ResponseEntity<?> getMessages(@RequestParam String userId) { ... }
```

- **Rate Limiter Starter** — `rate-limiter-spring-boot-starter` module
- Custom `@RateLimit` annotation
- AOP Aspect to intercept annotated methods
- `RateLimitFilter` (Servlet Filter) for endpoint-level protection
- **Business Service** uses the starter — no direct dependency on Rate Limiter Service
- Business Service can work with **in-memory limiter** (from starter) OR call **Rate Limiter Service** (via config toggle)

**Deliverable:** Reusable starter. Business Service uses it independently.

---

### Phase 7: ⚙️ Config Service — Centralized Rule Management
> **Goal:** Dedicated microservice for rate limit rules. All services fetch rules from here.

**Config Service REST API:**
```
GET    /api/v1/rules                    — List all rules
GET    /api/v1/rules/{domain}           — Get rules for a domain
POST   /api/v1/rules                    — Create a new rule
PUT    /api/v1/rules/{id}               — Update a rule
DELETE /api/v1/rules/{id}               — Delete a rule
```

**Rule Schema:**
```json
{
  "id": "rule-001",
  "domain": "business-service",
  "path": "/api/v1/messages/*",
  "method": "POST",
  "requestsPerWindow": 10,
  "windowSizeSeconds": 60,
  "keyResolver": "userId",
  "strategy": "sliding-window-counter",
  "failStrategy": "FAIL_OPEN",
  "priority": 1
}
```

- Rules stored in **H2/PostgreSQL** database
- **Rate Limiter Service** fetches rules from Config Service (with local caching)
- **API Gateway** also queries Config Service for gateway-level rules
- Rules can be updated at **runtime** — no restart needed
- Config Service has its **own rate limiting** (bootstrapped with in-memory)

**Deliverable:** Config Service running. Rate Limiter Service fetches rules dynamically.

---

### Phase 8: 🔴 Redis-Backed Distributed Rate Limiter
> **Goal:** Move from in-memory to Redis for true multi-instance rate limiting.

**Why This Matters in Microservices:**
```
                    ┌─────────────────────┐
   Client ──────→   │   API Gateway       │
                    │   (3 instances)      │
                    └─────────┬───────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
        ┌──────────┐   ┌──────────┐   ┌──────────┐
        │ RL Svc 1 │   │ RL Svc 2 │   │ RL Svc 3 │
        │ in-mem   │   │ in-mem   │   │ in-mem   │
        │ Count:33 │   │ Count:33 │   │ Count:33 │  ❌ Each has own counter!
        └──────────┘   └──────────┘   └──────────┘

                    vs.

                    ┌─────────────────────┐
   Client ──────→   │   API Gateway       │
                    │   (3 instances)      │
                    └─────────┬───────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
        ┌──────────┐   ┌──────────┐   ┌──────────┐
        │ RL Svc 1 │   │ RL Svc 2 │   │ RL Svc 3 │
        └─────┬────┘   └─────┬────┘   └─────┬────┘
              └───────────────┼───────────────┘
                              ▼
                    ┌─────────────────────┐
                    │   Redis Cluster     │
                    │   Count: 99         │  ✅ Single source of truth!
                    └─────────────────────┘
```

- `RedisRateLimiter` implements the same `RateLimiter` interface
- All Rate Limiter Service instances share state via **Redis**
- Redis **atomic operations** (`INCR`, `EXPIRE`, `SETNX`)
- Connection pooling with **Lettuce** (Spring Data Redis)
- TTL-based auto-cleanup of expired counters
- **Config toggle:** `rate-limiter.storage: redis` (vs `memory`)

**Deliverable:** Rate Limiter Service with Redis backend. Multi-instance tested.

---

### Phase 9: 🧵 Lua Scripts for Atomic Operations
> **Goal:** Eliminate race conditions in distributed rate limiting across microservices.

**The Race Condition Problem:**
```
RL Service 1:  GET count → 97    RL Service 2:  GET count → 97
               (both read 97)
RL Service 1:  INCR → 98         RL Service 2:  INCR → 98
               ❌ Two requests counted as ONE!
```

**The Lua Script Solution (atomic check + increment):**
```lua
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])

local current = tonumber(redis.call('GET', key) or "0")
if current + 1 > limit then
    return 0  -- REJECT
end

redis.call('INCR', key)
if current == 0 then
    redis.call('EXPIRE', key, window)
end
return 1  -- ALLOW
```

- Lua scripts run **atomically** inside Redis — no interleaving
- Check + increment + expire in a **single round-trip**
- Separate Lua scripts for each algorithm:
  - `fixed_window.lua`
  - `token_bucket.lua`
  - `sliding_window_log.lua`
  - `sliding_window_counter.lua`
- **Spring Redis `DefaultRedisScript`** integration

**Deliverable:** All Lua scripts. Race condition tests pass.

---

### Phase 10: 📊 Fail Strategy + Monitoring + Health Checks
> **Goal:** Handle Redis/config service failures gracefully. Observe everything.

**Fail Strategy (per service):**
```java
public enum FailStrategy {
    FAIL_OPEN,   // Service down → allow traffic (favor availability)
    FAIL_CLOSED  // Service down → block traffic (favor safety)
}
```

**What happens when services fail:**
```
┌─────────────────────────────────────────────────────────┐
│ Scenario                    │ Strategy                  │
├─────────────────────────────┼───────────────────────────┤
│ Redis is DOWN               │ Fail-open (allow traffic) │
│ Config Service is DOWN      │ Use cached rules          │
│ Rate Limiter Service DOWN   │ Gateway falls back to     │
│                             │ in-memory (starter)       │
│ Business Service is DOWN    │ Gateway returns 503       │
└─────────────────────────────┴───────────────────────────┘
```

- **Circuit Breaker** (Resilience4j) on inter-service calls
- **Fallback:** API Gateway has in-memory rate limiter as backup
- **Health Checks:** Spring Actuator `/health` on every service

**Monitoring (Micrometer + Prometheus + Grafana):**
- `rate_limiter_requests_total` — total requests
- `rate_limiter_allowed_total` — allowed
- `rate_limiter_rejected_total` — rejected (429)
- `rate_limiter_response_time_ms` — latency
- `service_up` — health of each microservice
- Grafana dashboards for real-time visibility
- Alert rules for rejection rate spikes

**Deliverable:** Circuit breaker + fallbacks + Grafana dashboards.

---

### Phase 11: 🎯 Traffic Simulator Service
> **Goal:** A dedicated service that mimics real-world traffic patterns — from idle to DDoS attack.

**Why?** A rate limiter that works at 10 QPS might behave differently at 10,000 QPS. This service lets you **see** how the system responds.

**6 Traffic Patterns:**
```
1. CONSTANT         2. RAMP UP           3. BURST
─────────────      ─────────────       ─────────────
│████████████      │        ██          │            ████
│████████████      │      ████          │            ████
│████████████      │    ██████          │            ████
└────────────      └────────────       └────────────

4. SINUSOIDAL       5. RANDOM            6. ATTACK
─────────────      ─────────────       ─────────────
│  ██  ██  ██      │█ █  █   █          │              ████████████
│██  ██  ██        │  █  █ █            │              ████████████
└────────────      └────────────       └────────────
```

**Example — Simulate a DDoS attack:**
```bash
curl -X POST http://localhost:8084/api/v1/simulator/start \
  -H "Content-Type: application/json" \
  -d '{
    "pattern": "RAMP",
    "startQps": 10,
    "endQps": 5000,
    "durationSeconds": 120,
    "targetUrl": "http://localhost:8080/api/v1/users"
  }'
```

**What you'll see on Grafana:**
```
QPS Over Time:           Rejection Rate:
│      ████             │              ████
│    ████████           │            ████████
│  ████████████         │          ████████████
│████████████████       │        ████████████████
└────────────────       └────────────────────────
Normal → Attack          Normal → Attack
                         (rate limiter kicks in!)
```

**Deliverable:** Traffic Simulator running. Grafana shows live QPS + rejection curves.

---

### Phase 12: 🧪 Final Integration Tests + Docker + Documentation
> **Goal:** Everything runs with `docker-compose up`. Full test coverage.

- **Docker Compose** — one command to start everything:
  ```bash
  docker-compose up -d
  # Starts: API Gateway, Rate Limiter, Config Service, Business Service, Traffic Simulator, Redis, Prometheus, Grafana
  ```
- **Integration tests** — multi-threaded, multi-service scenarios
- **Load test with Traffic Simulator** — validate accuracy under realistic load
- **API documentation** — SpringDoc OpenAPI (Swagger) for each service
- **Architecture Decision Records** (ADRs) — why each choice was made
- **Final README** — setup instructions, diagrams, examples

**Deliverable:** `docker-compose up` runs the full system. Tests pass. Traffic Simulator can drive the system from idle to attack.

---

## Rate Limiting Algorithms — Comparison

| Algorithm | Accuracy | Memory | Allows Bursts | Complexity | Best For |
|---|---|---|---|---|---|
| **Fixed Window** | ⚠️ Low (boundary) | 🟢 Low | ✅ Yes | ⭐ Simple | Quick prototype |
| **Token Bucket** | 🟢 Good | 🟢 Low | ✅ Yes | ⭐ Simple | Burst-friendly APIs |
| **Leaky Bucket** | 🟢 Good | 🟢 Low | ❌ Smoothed | ⭐ Simple | Traffic shaping |
| **Sliding Window Log** | 🟢 High | 🔴 High | ❌ No | ⭐⭐ Moderate | High-accuracy needs |
| **Sliding Window Counter** | 🟢 High (approx) | 🟢 Low | ⚠️ Slight | ⭐ Simple | **Production default** |

---

## Microservices Architecture

```
    ┌──────────────┐      ┌──────────────────────┐
    │ 🎯 TRAFFIC   │      │     Real Clients     │
    │ SIMULATOR    │      │  (curl / Postman /    │
    │ (8084)       │      │   Frontend App)       │
    │              │      └──────────┬───────────┘
    │ Mimics real  │                 │
    │ users: low   │─────────────────┤
    │ QPS → high   │                 │
    │ QPS → attack │                 │
    └──────┬───────┘                 │
           │                         │
           └─────────────┬───────────┘
                         ▼
       ┌─────────────────────────────────────┐
       │        🚪 API GATEWAY (8080)         │
       │                                     │
       │  ┌─────────────────────────────┐    │
       │  │  Rate Limit Filter           │    │
       │  │  (calls RL Service or        │    │
       │  │   falls back to in-memory)   │    │
       │  └─────────────┬───────────────┘    │
       │                │                     │
       │  ┌─────────────▼───────────────┐    │
       │  │  Routing / Load Balancing    │    │
       │  └─────────────┬───────────────┘    │
       └────────────────┼─────────────────────┘
                        │
        ┌───────────────┼───────────────────┐
        │               │                   │
        ▼               ▼                   ▼
 ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐
 │ 🛡️ RATE      │  │ ⚙️ CONFIG    │  │ 📦 BUSINESS      │
 │ LIMITER      │  │ SERVICE      │  │ SERVICE          │
 │ SERVICE      │  │ (8082)       │  │ (8083)           │
 │ (8081)       │  │              │  │                  │
 │              │  │ Rules CRUD   │  │ /api/v1/users    │
 │ Algorithms:  │  │ DB-backed    │  │ /api/v1/orders   │
 │ - FixedWin   │  │ Runtime      │  │ /api/v1/products │
 │ - TokenBuck  │  │ updates      │  │                  │
 │ - SlideLog   │  │              │  │ Uses rate-       │
 │ - SlideCntr  │  │              │  │ limiter-starter  │
 └──────┬───────┘  └──────┬───────┘  └────────┬─────────┘
        │                 │                    │
        └─────────────────┼────────────────────┘
                          ▼
           ┌──────────────────────────┐
           │     🔴 Redis (6379)       │
           │                          │
           │  Shared rate limit state  │
           │  Lua scripts for atomic   │
           │  operations               │
           └──────────────────────────┘
                          │
                          ▼
           ┌──────────────────────────┐
           │  📊 Prometheus (9090)     │
           │  📈 Grafana (3000)        │
           │                          │
           │  Metrics + Dashboards     │
           └──────────────────────────┘
```

---

## Service Descriptions

### 1. 🚪 API Gateway Service (`api-gateway`)
- **Port:** 8080
- **Role:** Single entry point for all client requests
- **Responsibilities:**
  - Receive all incoming HTTP requests
  - Apply rate limiting (via Rate Limiter Service or in-memory fallback)
  - Route requests to appropriate downstream service
  - Load balancing across service instances
  - SSL termination
  - Request/response logging

### 2. 🛡️ Rate Limiter Service (`rate-limiter-service`)
- **Port:** 8081
- **Role:** Dedicated microservice for rate limiting logic
- **Responsibilities:**
  - Expose `POST /api/v1/check` endpoint (check + consume)
  - Expose `GET /api/v1/usage/{key}` endpoint (current usage)
  - Expose `DELETE /api/v1/reset/{key}` endpoint (reset counter)
  - Implement all 4 algorithms (Fixed Window, Token Bucket, Sliding Window Log, Sliding Window Counter)
  - Fetch rules from Config Service (with local caching + TTL)
  - Store counters in Redis (distributed) or in-memory (single instance)
  - Execute Lua scripts for atomic operations
  - Expose Prometheus metrics

### 3. ⚙️ Config Service (`config-service`)
- **Port:** 8082
- **Role:** Centralized rule management
- **Responsibilities:**
  - CRUD API for rate limit rules
  - Store rules in database (H2 / PostgreSQL)
  - Notify Rate Limiter Service of rule changes (webhook or polling)
  - Admin dashboard (optional — Spring MVC)
  - Rule versioning and audit trail

### 4. 📦 Business Service (`business-service`)
- **Port:** 8083
- **Role:** Sample downstream service to demonstrate rate limiting in action
- **Responsibilities:**
  - Expose sample API endpoints (`/api/v1/users`, `/api/v1/orders`, etc.)
  - Include `rate-limiter-spring-boot-starter` dependency
  - Demonstrate both `@RateLimit` annotation AND gateway-level limiting
  - Return meaningful business responses

### 5. 🎯 Traffic Simulator (`traffic-simulator`)
- **Port:** 8084
- **Role:** Mimics real-world traffic patterns to test and visualize the rate limiter under varying load
- **Why this matters:** A rate limiter that works at 10 QPS might behave differently at 10,000 QPS. This service lets you **see** how the system responds from idle → normal → heavy → attack traffic.
- **Responsibilities:**
  - Generate configurable traffic patterns (constant, burst, ramp, sinusoidal, random)
  - Vary QPS over time: **low → spike → steady → burst → drop → recovery**
  - Send requests to the API Gateway (just like real users)
  - Track and report: request count, allowed, rejected, latency, throughput
  - Expose a **control API** to start/stop/adjust traffic on the fly
  - Stream live metrics to Grafana dashboard

**Traffic Patterns Supported:**

```
1. CONSTANT         2. RAMP UP           3. BURST
─────────────      ─────────────       ─────────────
Requests/sec       Requests/sec        Requests/sec
│████████████      │        ██          │            ████
│████████████      │      ████          │            ████
│████████████      │    ██████          │            ████
│████████████      │  ████████          │            ████
└────────────      └────────────       └────────────
Time →              Time →              Time →

4. SINUSOIDAL       5. RANDOM            6. ATTACK
─────────────      ─────────────       ─────────────
Requests/sec       Requests/sec        Requests/sec
│  ██  ██  ██      │█ █  █   █          │              ████████████
│██  ██  ██        │  █  █ █            │              ████████████
│    ██            │█   █   █ █         │              ████████████
└────────────      └────────────       └────────────
Time →              Time →              Time →
```

**Control API:**
```
POST   /api/v1/simulator/start          — Start traffic generation
POST   /api/v1/simulator/stop           — Stop traffic generation
PUT    /api/v1/simulator/pattern        — Change pattern on the fly
GET    /api/v1/simulator/stats          — Live stats (QPS, allowed, rejected)
GET    /api/v1/simulator/timeline       — Full timeline of QPS + results
```

**Example — Simulate a DDoS attack:**
```bash
# Start with normal traffic (50 QPS)
curl -X POST http://localhost:8084/api/v1/simulator/start \
  -H "Content-Type: application/json" \
  -d '{
    "pattern": "RAMP",
    "startQps": 50,
    "endQps": 5000,
    "durationSeconds": 120,
    "targetUrl": "http://localhost:8080/api/v1/users"
  }'

# Watch Grafana dashboard in real-time
# See: QPS rises → rate limiter starts rejecting → 429s increase
# Then: attack ends → QPS drops → normal traffic resumes
```

**What you'll see on the dashboard:**
```
QPS Over Time:           Rejection Rate:
│      ████             │              ████
│    ████████           │            ████████
│  ████████████         │          ████████████
│████████████████       │        ████████████████
└────────────────       └────────────────────────
Normal → Attack          Normal → Attack
                         (rate limiter kicks in!)
```

### 6. 📚 Common Module (`common`)
- **Role:** Shared code across all services
- **Contents:**
  - `RateLimitRequest` / `RateLimitResponse` DTOs
  - `RateLimitExceededException` + `GlobalExceptionHandler`
  - `RateLimitMetrics` utility
  - Shared constants and enums

---

## Inter-Service Communication

```
┌─────────────────────┐     HTTP REST      ┌─────────────────────┐
│   API Gateway       │ ─────────────────→  │  Rate Limiter       │
│                     │ ←─────────────────  │  Service            │
│                     │  {allowed: true}    │                     │
└─────────────────────┘                     └─────────────────────┘
                                                       │
                                                       │ HTTP REST
                                                       ▼
                                              ┌─────────────────────┐
                                              │   Config Service    │
                                              │   (fetch rules)     │
                                              └─────────────────────┘

┌─────────────────────┐     HTTP REST      ┌─────────────────────┐
│   API Gateway       │ ─────────────────→  │  Business Service   │
│                     │ ←─────────────────  │                     │
│                     │  (actual response)  │                     │
└─────────────────────┘                     └─────────────────────┘

All Services ──────────────────────────→     ┌─────────────────────┐
                                             │   Redis             │
                                             │   (shared state)    │
                                             └─────────────────────┘

All Services ──────────────────────────→     ┌─────────────────────┐
                                             │   Prometheus        │
                                             │   (metrics scrape)  │
                                             └─────────────────────┘
```

### Communication Patterns:

| From | To | Protocol | Purpose |
|---|---|---|---|
| API Gateway | Rate Limiter Service | HTTP REST | Check rate limit before forwarding |
| Rate Limiter Service | Config Service | HTTP REST | Fetch rate limit rules |
| Rate Limiter Service | Redis | TCP (Lettuce) | Read/write counters + Lua scripts |
| API Gateway | Business Service | HTTP REST | Forward allowed requests |
| All Services | Prometheus | HTTP (Actuator) | Expose metrics for scraping |
| All Services | Redis | TCP (Lettuce) | Health checks, distributed state |

---

## Project Structure — All Services

```
rate-limiter-system/
│
├── pom.xml                                          # Parent Maven POM
├── docker-compose.yml                               # Full stack orchestration
├── README.md                                        # This file
│
├── common/                                          # 📚 Shared Module
│   ├── pom.xml
│   └── src/main/java/com/ratelimiter/common/
│       ├── dto/
│       │   ├── RateLimitRequest.java
│       │   └── RateLimitResponse.java
│       ├── exception/
│       │   ├── RateLimitExceededException.java
│       │   └── GlobalExceptionHandler.java
│       ├── enums/
│       │   ├── Algorithm.java                       # FIXED_WINDOW, TOKEN_BUCKET, etc.
│       │   ├── FailStrategy.java                    # FAIL_OPEN, FAIL_CLOSED
│       │   └── KeyResolverType.java                 # IP, USER_ID, API_KEY
│       └── util/
│           └── RateLimiterMetrics.java
│
├── api-gateway/                                     # 🚪 API Gateway Service
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ratelimiter/gateway/
│       │   ├── ApiGatewayApplication.java
│       │   ├── config/
│       │   │   ├── GatewayConfig.java               # Route definitions
│       │   │   └── RateLimitClientConfig.java       # WebClient for RL Service
│       │   ├── filter/
│       │   │   ├── RateLimitFilter.java             # Pre-filter: calls RL Service
│       │   │   ├── FallbackRateLimiter.java         # In-memory fallback
│       │   │   └── LoggingFilter.java               # Request/response logging
│       │   └── client/
│       │       └── RateLimiterClient.java           # WebClient wrapper
│       └── resources/
│           ├── application.yml
│           └── bootstrap.yml
│
├── rate-limiter-service/                            # 🛡️ Rate Limiter Service
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ratelimiter/service/
│       │   ├── RateLimiterServiceApplication.java
│       │   ├── config/
│       │   │   ├── RedisConfig.java
│       │   │   ├── RateLimiterProperties.java
│       │   │   └── AlgorithmConfig.java
│       │   ├── controller/
│       │   │   └── RateLimitController.java         # POST /check, GET /usage
│       │   ├── service/
│       │   │   ├── RateLimitService.java            # Orchestrator
│       │   │   ├── RuleFetchService.java            # Fetches from Config Service
│       │   │   └── RuleCacheService.java            # Local cache with TTL
│       │   ├── limiter/
│       │   │   ├── RateLimiter.java                 # Core interface
│       │   │   ├── RateLimitResult.java
│       │   │   └── impl/
│       │   │       ├── FixedWindowRateLimiter.java
│       │   │       ├── TokenBucketRateLimiter.java
│       │   │       ├── SlidingWindowLogRateLimiter.java
│       │   │       └── SlidingWindowCounterRateLimiter.java
│       │   ├── redis/
│       │   │   ├── RedisRateLimiter.java
│       │   │   └── RedisRuleRepository.java
│       │   └── metrics/
│       │       └── RateLimiterMetrics.java
│       ├── resources/
│       │   ├── application.yml
│       │   └── lua/
│       │       ├── fixed_window.lua
│       │       ├── token_bucket.lua
│       │       ├── sliding_window_log.lua
│       │       └── sliding_window_counter.lua
│       └── test/
│           └── java/com/ratelimiter/service/
│               ├── limiter/
│               │   ├── FixedWindowRateLimiterTest.java
│               │   ├── TokenBucketRateLimiterTest.java
│               │   ├── SlidingWindowLogRateLimiterTest.java
│               │   └── SlidingWindowCounterRateLimiterTest.java
│               ├── redis/
│               │   └── RedisRateLimiterIntegrationTest.java
│               └── controller/
│                   └── RateLimitControllerTest.java
│
├── config-service/                                  # ⚙️ Config Service
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ratelimiter/config/
│       │   ├── ConfigServiceApplication.java
│       │   ├── controller/
│       │   │   └── RuleController.java              # CRUD API
│       │   ├── model/
│       │   │   ├── RateLimitRule.java               # JPA Entity
│       │   │   └── RuleRepository.java              # Spring Data JPA
│       │   ├── service/
│       │   │   └── RuleService.java
│       │   └── exception/
│       │       └── RuleNotFoundException.java
│       └── resources/
│           ├── application.yml
│           ├── data.sql                             # Seed rules
│           └── schema.sql
│
├── business-service/                                # 📦 Business Service
│   ├── pom.xml                                      # Includes rate-limiter-starter
│   └── src/main/
│       ├── java/com/ratelimiter/business/
│       │   ├── BusinessServiceApplication.java
│       │   ├── controller/
│       │   │   ├── UserController.java
│       │   │   ├── OrderController.java
│       │   │   └── ProductController.java
│       │   ├── model/
│       │   │   ├── User.java
│       │   │   ├── Order.java
│       │   │   └── Product.java
│       │   └── service/
│       │       ├── UserService.java
│       │       ├── OrderService.java
│       │       └── ProductService.java
│       └── resources/
│           └── application.yml
│
├── traffic-simulator/                               # 🎯 Traffic Simulator Service
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ratelimiter/simulator/
│       │   ├── TrafficSimulatorApplication.java
│       │   ├── config/
│       │   │   └── SimulatorProperties.java        # Configurable QPS, patterns
│       │   ├── controller/
│       │   │   └── SimulatorController.java        # Control API (start/stop/stats)
│       │   ├── engine/
│       │   │   ├── TrafficEngine.java              # Core engine — sends requests
│       │   │   ├── TrafficPattern.java             # Interface for patterns
│       │   │   ├── pattern/
│       │   │   │   ├── ConstantPattern.java         # Steady QPS
│       │   │   │   ├── RampPattern.java             # Linear ramp up/down
│       │   │   │   ├── BurstPattern.java            # Periodic bursts
│       │   │   │   ├── SinusoidalPattern.java       # Wave pattern
│       │   │   │   ├── RandomPattern.java           # Random QPS
│       │   │   │   └── AttackPattern.java           # Simulate DDoS
│       │   │   └── TrafficScenario.java             # Multi-phase scenario
│       │   ├── model/
│       │   │   ├── TrafficConfig.java               # Request config
│       │   │   ├── TrafficStats.java                # Live statistics
│       │   │   └── RequestResult.java               # Per-request result
│       │   └── reporter/
│       │       ├── StatsReporter.java               # Console + metrics reporter
│       │       └── MetricsExporter.java             # Push to Prometheus
│       └── resources/
│           └── application.yml
│
├── rate-limiter-starter/                            # 📚 Spring Boot Starter
│   ├── pom.xml
│   └── src/main/java/com/ratelimiter/starter/
│       ├── RateLimiterAutoConfiguration.java
│       ├── annotation/
│       │   └── RateLimit.java                       # @RateLimit annotation
│       ├── aspect/
│       │   └── RateLimitAspect.java                 # AOP interceptor
│       ├── filter/
│       │   └── RateLimitFilter.java                 # Servlet filter
│       ├── resolver/
│       │   ├── KeyResolver.java                     # Interface
│       │   ├── IpKeyResolver.java
│       │   ├── UserIdKeyResolver.java
│       │   └── ApiKeyKeyResolver.java
│       └── config/
│           └── RateLimiterStarterProperties.java
│
└── monitoring/                                      # 📊 Monitoring Config
    ├── prometheus/
    │   └── prometheus.yml
    └── grafana/
        └── dashboards/
            └── rate-limiter-dashboard.json
```

---

## Tech Stack

| Component | Technology | Why |
|---|---|---|
| Language | Java 17 | LTS, modern features |
| Framework | Spring Boot 3.x | Microservices ecosystem |
| Build | Maven (multi-module) | Dependency management |
| API Gateway | Spring Cloud Gateway | Reactive, lightweight |
| Service Communication | Spring WebClient | Non-blocking HTTP client |
| In-Memory Store | ConcurrentHashMap / Caffeine | Fast, local |
| Distributed Store | Redis 7.x | Shared state, Lua support |
| Redis Client | Lettuce (Spring Data Redis) | Connection pooling, async |
| Config DB | H2 (dev) / PostgreSQL (prod) | Rule persistence |
| Circuit Breaker | Resilience4j | Fault tolerance |
| Metrics | Micrometer + Prometheus | Time-series metrics |
| Dashboards | Grafana | Visualization |
| Testing | JUnit 5, Mockito, Testcontainers | Unit + integration |
| API Docs | SpringDoc OpenAPI | Swagger UI |
| Containerization | Docker + Docker Compose | Local dev environment |

---

## The Journey Visualized

```
Phase 1:   Empty project → All 5 services start with Docker
              │
Phase 2-5: Rate Limiter Service learns all 4 algorithms (in-memory)
              │
Phase 6:   Any service can use rate limiting via @RateLimit annotation
              │
Phase 7:   Rules live in Config Service, not hardcoded
              │
Phase 8:   Redis stores counters (works across multiple server instances)
              │
Phase 9:   Lua scripts prevent race conditions
              │
Phase 10:  If anything fails → graceful fallback + monitoring dashboards
              │
Phase 11:  Traffic Simulator mimics real users (low QPS → DDoS attack)
              │
Phase 12:  Everything orchestrated with Docker Compose + tests
```

---

## What's The End Result?

After all 12 phases, here's what you'll have:

```bash
# Start the entire system with ONE command
docker-compose up -d

# Client makes requests — rate limiter decides
curl http://localhost:8080/api/v1/users    → 200 OK ✅
curl http://localhost:8080/api/v1/users    → 200 OK ✅
curl http://localhost:8080/api/v1/users    → 200 OK ✅
curl http://localhost:8080/api/v1/users    → 429 Too Many Requests ❌

# Launch the Traffic Simulator — watch QPS climb from 10 to 5000
curl -X POST http://localhost:8084/api/v1/simulator/start \
  -d '{"pattern":"RAMP","startQps":10,"endQps":5000,"durationSeconds":120}'

# See live metrics on Grafana dashboard
# Watch: QPS rises → rate limiter kicks in → 429s increase → attack ends → recovery
open http://localhost:3000
```

---

## How to Run

```bash
# 1. Start everything
docker-compose up -d

# 2. Verify services
curl http://localhost:8080/actuator/health   # API Gateway
curl http://localhost:8081/actuator/health   # Rate Limiter Service
curl http://localhost:8082/actuator/health   # Config Service
curl http://localhost:8083/actuator/health   # Business Service
curl http://localhost:8084/actuator/health   # Traffic Simulator

# 3. Create a rate limit rule
curl -X POST http://localhost:8082/api/v1/rules \
  -H "Content-Type: application/json" \
  -d '{
    "domain": "business-service",
    "path": "/api/v1/users",
    "method": "GET",
    "requestsPerWindow": 5,
    "windowSizeSeconds": 60,
    "keyResolver": "IP_ADDRESS",
    "strategy": "SLIDING_WINDOW_COUNTER"
  }'

# 4. Test rate limiting manually
for i in {1..10}; do
  echo "Request $i:"
  curl -s -o /dev/null -w "HTTP %{http_code}\n" http://localhost:8083/api/v1/users
done
# First 5 → 200 OK
# Next 5 → 429 Too Many Requests

# 5. Launch the Traffic Simulator!
# Start with a ramp from 10 to 5000 QPS over 2 minutes
curl -X POST http://localhost:8084/api/v1/simulator/start \
  -H "Content-Type: application/json" \
  -d '{
    "pattern": "RAMP",
    "startQps": 10,
    "endQps": 5000,
    "durationSeconds": 120,
    "targetUrl": "http://localhost:8080/api/v1/users",
    "keyResolver": "IP_ADDRESS"
  }'

# 6. Watch the dashboard — see QPS rise, rejections increase
open http://localhost:3000    # Grafana dashboard
open http://localhost:9090    # Prometheus targets

# 7. Check live simulator stats
curl http://localhost:8084/api/v1/simulator/stats
```

---

## How to Follow Along

> 📌 **We build one phase at a time.** Each phase is fully working before moving to the next.

| Phase | Status | What's Built |
|---|---|---|
| Phase 1 | ⬜ Pending | Multi-module Maven + Docker Compose + all services start |
| Phase 2 | ⬜ Pending | Fixed Window Counter in Rate Limiter Service |
| Phase 3 | ⬜ Pending | Token Bucket in Rate Limiter Service |
| Phase 4 | ⬜ Pending | Sliding Window Log in Rate Limiter Service |
| Phase 5 | ⬜ Pending | Sliding Window Counter (hybrid) ⭐ |
| Phase 6 | ⬜ Pending | @RateLimit starter library for downstream services |
| Phase 7 | ⬜ Pending | Config Service with rule CRUD API |
| Phase 8 | ⬜ Pending | Redis-backed distributed rate limiting |
| Phase 9 | ⬜ Pending | Lua scripts for atomic operations |
| Phase 10 | ⬜ Pending | Circuit breaker + fallbacks + Grafana dashboards |
| Phase 11 | ⬜ Pending | Traffic Simulator with 6 patterns (constant, ramp, burst, sinusoidal, random, attack) |
| Phase 12 | ⬜ Pending | Docker Compose orchestration + full tests |

---

## References

- 📘 **System Design Interview — Alex Xu, Chapter 4**: "Design a Rate Limiter"
- 🌐 [Spring Cloud Gateway](https://spring.io/projects/spring-cloud-gateway)
- 🌐 [Redis Rate Limiting Patterns](https://redis.io/docs/apply/)
- 🌐 [Stripe Rate Limiting](https://stripe.com/blog/rate-limiters)
- 🌐 [Cloudflare Rate Limiting](https://www.cloudflare.com/learning/bots/what-is-rate-limiting/)
- 🌐 [Resilience4j](https://resilience4j.readme.io/)

---

> **Say "proceed" and we'll start with Phase 1: Multi-Module Maven Project + Docker Compose.**
