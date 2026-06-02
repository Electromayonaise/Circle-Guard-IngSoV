# Design Patterns — gateway-service Resilience

Implemented as part of HU: *"Como arquitecto, quiero patrones de resiliencia en el gateway-service para las llamadas a servicios dependientes"*

---

## 1. Context

The **gateway-service** (`port 8087`) validates QR tokens for campus access. To determine a user's health status it calls **promotion-service** (`port 8088`) at:

```
GET /api/v1/health/status/{anonymousId}
→ { "status": "CLEAR" | "CONTAGIED" | "POTENTIAL" | ... }
```

Two resilience patterns protect this call:

| Pattern | Responsibility |
|---|---|
| **Retry + Exponential Backoff** | Tolerates transient failures (network blip, slow start) |
| **Circuit Breaker** | Stops calling a degraded service; returns cached state instead |

---

## 2. Pattern 1 — Retry with Exponential Backoff

### What it does
When the call to promotion-service fails, the gateway automatically retries before giving up. Each attempt waits progressively longer to avoid flooding an already-struggling service.

### Configuration (`application.yml`)
```yaml
resilience4j:
  retry:
    retryAspectOrder: 2   # inner aspect — retries the HTTP call before CB sees result
    instances:
      promotionService:
        maxAttempts: 3
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - org.springframework.web.client.RestClientException
          - java.io.IOException
```

### Backoff Timeline

```
Attempt 1 ──✗── wait 500ms ──► Attempt 2 ──✗── wait 1000ms ──► Attempt 3 ──✗── FAIL
                                                                               ↓
                                                            Circuit Breaker records failure
```

### Code
```java
// PromotionClient.java
@CircuitBreaker(name = "promotionService", fallbackMethod = "getStatusFromCache")
@Retry(name = "promotionService")
public String getHealthStatus(String anonymousId) {
    String url = promotionServiceUrl + "/api/v1/health/status/" + anonymousId;
    Map<?, ?> body = restTemplate.getForObject(url, Map.class);
    return body != null ? (String) body.get("status") : "UNKNOWN";
}
```

---

## 3. Pattern 2 — Circuit Breaker

### What it does
Tracks the failure rate of calls to promotion-service over a sliding window. When failures exceed a threshold the circuit **opens**: subsequent calls skip promotion-service entirely and return the cached Redis status, giving the downstream service time to recover.

### State Machine

```
          failures >= 50% of 10 calls
CLOSED ─────────────────────────────────► OPEN
  ▲                                         │
  │   success                               │ after 10s waitDurationInOpenState
  │                                         ▼
  └─────────────────────────────────── HALF-OPEN
         (3 probe calls allowed)
```

| State | Behaviour |
|---|---|
| **CLOSED** | Normal — calls go to promotion-service (via Retry) |
| **OPEN** | Fast-fail — fallback fires immediately, no HTTP call |
| **HALF-OPEN** | Probe — 3 calls allowed; if they succeed, transitions back to CLOSED |

### Configuration (`application.yml`)
```yaml
resilience4j:
  circuitbreaker:
    circuitBreakerAspectOrder: 1   # outer aspect — owns the fallback method
    instances:
      promotionService:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 3
        recordExceptions:
          - org.springframework.web.client.RestClientException
          - java.io.IOException
```

### Fallback (Redis cache)
```java
// PromotionClient.java — fallback for CircuitBreaker
String getStatusFromCache(String anonymousId, Exception ex) {
    log.warn("promotion-service unavailable for anonymousId={}, using Redis cache. cause={}", ...);
    String cached = redisTemplate.opsForValue().get("user:status:" + anonymousId);
    return cached != null ? cached : "UNKNOWN";
}
```

The cached value in Redis is written by **promotion-service** whenever a user's health status changes (via `HealthStatusService`). The gateway reads it only as a fallback.

---

## 4. Combined Call Flow

```
GateController.validate(token)
        │
        ▼
QrValidationService.validateToken(token)
        │  parses JWT → gets anonymousId
        ▼
PromotionClient.getHealthStatus(anonymousId)
        │
        │  [CircuitBreaker — OUTER, order=1]
        │  ┌─ CB OPEN? ──────────────────────────────────────────────────────┐
        │  │                                                                  │
        │  │  CB CLOSED/HALF-OPEN                                            ▼
        │  │  [Retry — INNER, order=2]                             getStatusFromCache()
        │  │  ┌─ attempt 1 ──► HTTP GET /health/status/{id}             (reads Redis)
        │  │  │         fail? wait 500ms
        │  │  ├─ attempt 2 ──► HTTP GET /health/status/{id}
        │  │  │         fail? wait 1000ms
        │  │  └─ attempt 3 ──► HTTP GET /health/status/{id}
        │  │            fail? ──────────────────────────────────────────────►┘
        │  │            ok?  → return status
        │  └──────────────────────────────────────────────────────────────────┘
        │
        ▼
status ∈ {CONTAGIED, POTENTIAL} → ValidationResult(false, "RED",  "Access Denied")
status = anything else           → ValidationResult(true,  "GREEN", "Welcome to Campus")
```

---

## 5. Demo — Observing the Patterns in the Cluster

### Prerequisites
```bash
kubectl config set-context --current --namespace=dev
# gateway is exposed at https://gateway-dev.circleguard.local
```

### Demo A — Retry then Fallback

1. Scale promotion-service to 0 replicas:
   ```bash
   kubectl scale deployment promotion-service --replicas=0
   ```
2. Send a validate request:
   ```bash
   curl -sk -X POST https://gateway-dev.circleguard.local/api/v1/gate/validate \
     -H "Content-Type: application/json" \
     -d '{"token": "<valid-qr-token>"}'
   ```
3. Watch the gateway pod logs — you will see 3 retry attempts logged, then the fallback warning:
   ```
   WARN  PromotionClient - promotion-service unavailable for anonymousId=..., using Redis cache.
   ```
4. The response still returns `GREEN` or `RED` based on the cached Redis value.

### Demo B — Circuit Breaker Opens

After enough failures (≥5 out of a sliding window of 10), the circuit opens.

1. Check circuit state via Actuator:
   ```bash
   kubectl exec -it deploy/gateway-service -- \
     curl -s localhost:8087/actuator/circuitbreakers | jq .
   ```
   Look for `"state": "OPEN"` on `promotionService`.

2. While OPEN, the gateway logs will no longer show retry attempts — the fallback fires immediately.

3. Scale promotion-service back up:
   ```bash
   kubectl scale deployment promotion-service --replicas=1
   ```
   After `waitDurationInOpenState: 10s`, the CB transitions to HALF-OPEN and 3 probe calls are allowed. On success it returns to CLOSED.

### Demo C — Run the Resilience Test Directly
```bash
cd services/circleguard-gateway-service
../../gradlew test --tests "com.circleguard.gateway.client.PromotionClientResilienceTest"
```
The test uses Testcontainers (Redis) + `MockRestServiceServer` (simulates promotion-service returning 500) to verify:
- Exactly 3 HTTP calls are made (Retry)
- The fallback returns the Redis-cached value
- When the CB is manually opened, 0 HTTP calls are made

---

## 6. Files Changed

| File | Change |
|---|---|
| `build.gradle.kts` | Added `resilience4j-spring-boot3:2.2.0`, `spring-boot-starter-aop` |
| `src/main/resources/application.yml` | Added `services.promotion-url`, `resilience4j` config blocks |
| `src/main/java/.../client/PromotionClient.java` | **New** — HTTP client with `@CircuitBreaker` + `@Retry` + Redis fallback |
| `src/main/java/.../config/RestTemplateConfig.java` | **New** — `RestTemplate` bean with timeouts |
| `src/main/java/.../service/QrValidationService.java` | Replaced direct Redis lookup with `PromotionClient.getHealthStatus()` |
| `src/test/java/.../client/PromotionClientResilienceTest.java` | **New** — Integration test demonstrating both patterns |
| `src/test/java/.../service/QrValidationServiceTest.java` | Updated to mock `PromotionClient` instead of `StringRedisTemplate` |
| `src/test/java/.../service/QrStatusCacheIntegrationTest.java` | Updated to use `@MockBean PromotionClient` |
| `src/test/resources/application.yml` | Added `services.promotion-url`, fast retry for tests (`waitDuration: 10ms`) |
