# Patrones de Resiliencia y Configuración

Implementados como parte de las historias de usuario:
- *"Como arquitecto, quiero patrones de resiliencia en el gateway-service para las llamadas a servicios dependientes"*
- *"Como product owner, quiero poder activar/desactivar el módulo de analytics del dashboard sin redespliegue"*

---

## Patrón 1 — Retry con Exponential Backoff

### Contexto

El **gateway-service** (`puerto 8087`) valida tokens QR de acceso al campus. Para determinar el estado de salud del usuario llama a **promotion-service** (`puerto 8088`):

```
GET /api/v1/health/status/{anonymousId}
→ { "status": "CLEAR" | "CONTAGIED" | "POTENTIAL" | ... }
```

### Qué hace

Cuando la llamada a `promotion-service` falla, el gateway reintenta automáticamente antes de rendirse. Cada intento espera progresivamente más tiempo para no saturar un servicio que ya está bajo presión.

### Configuración (`application.yml`)

```yaml
resilience4j:
  retry:
    retryAspectOrder: 2   # aspecto interno — reintenta la llamada HTTP antes de que el CB vea el resultado
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

### Timeline de backoff

```
Intento 1 --X-- espera 500ms --> Intento 2 --X-- espera 1000ms --> Intento 3 --X-- FALLO
                                                                                   ↓
                                                             Circuit Breaker registra el fallo
```

### Código

```java
// gateway-service/src/main/java/com/circleguard/gateway/client/PromotionClient.java
@CircuitBreaker(name = "promotionService", fallbackMethod = "getStatusFromCache")
@Retry(name = "promotionService")
public String getHealthStatus(String anonymousId) {
    String url = promotionServiceUrl + "/api/v1/health/status/" + anonymousId;
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(buildServiceToken());
    ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    Map<?, ?> body = response.getBody();
    return body != null ? (String) body.get("status") : "UNKNOWN";
}
```

---

## Patrón 2 — Circuit Breaker

### Qué hace

Monitorea la tasa de fallos de las llamadas a `promotion-service` en una ventana deslizante. Cuando los fallos superan el umbral, el circuito **abre**: las llamadas siguientes se saltan directamente al fallback (Redis cache), dando tiempo al servicio caído para recuperarse.

### Máquina de estados

```
          fallos >= 50% de 10 llamadas
CLOSED ──────────────────────────────────► OPEN
  ▲                                          │
  │   éxito                                 │ después de 10s waitDurationInOpenState
  │                                          ▼
  └──────────────────────────────────── HALF-OPEN
          (3 llamadas de sondeo permitidas)
```

| Estado | Comportamiento |
|--------|---------------|
| **CLOSED** | Normal — las llamadas van a `promotion-service` (vía Retry) |
| **OPEN** | Fast-fail — el fallback se activa inmediatamente, sin llamada HTTP |
| **HALF-OPEN** | Sondeo — se permiten 3 llamadas; si tienen éxito, vuelve a CLOSED |

### Configuración (`application.yml`)

```yaml
resilience4j:
  circuitbreaker:
    circuitBreakerAspectOrder: 1   # aspecto externo — posee el método de fallback
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

### Fallback (caché Redis)

```java
// PromotionClient.java — fallback del CircuitBreaker
String getStatusFromCache(String anonymousId, Exception ex) {
    log.warn("promotion-service unavailable for anonymousId={}, using Redis cache. cause={}", anonymousId, ex.getMessage());
    String cached = redisTemplate.opsForValue().get("user:status:" + anonymousId);
    return cached != null ? cached : "UNKNOWN";
}
```

El valor cacheado en Redis lo escribe `promotion-service` cada vez que cambia el estado de salud de un usuario. El gateway lo lee únicamente como fallback.

---

## Flujo combinado de llamada

```
GateController.validate(token)
        │
        ▼
QrValidationService.validateToken(token)
        │  parsea JWT → obtiene anonymousId
        ▼
PromotionClient.getHealthStatus(anonymousId)
        │
        │  [CircuitBreaker — EXTERNO, orden=1]
        │  ┌─ CB OPEN? ─────────────────────────────────────────────────────┐
        │  │                                                                 │
        │  │  CB CLOSED/HALF-OPEN                                           ▼
        │  │  [Retry — INTERNO, orden=2]                        getStatusFromCache()
        │  │  ┌─ intento 1 ──► HTTP GET /health/status/{id}        (lee Redis)
        │  │  │         fallo? espera 500ms
        │  │  ├─ intento 2 ──► HTTP GET /health/status/{id}
        │  │  │         fallo? espera 1000ms
        │  │  └─ intento 3 ──► HTTP GET /health/status/{id}
        │  │            fallo? ─────────────────────────────────────────────►┘
        │  │            ok?  → retorna status
        │  └─────────────────────────────────────────────────────────────────┘
        │
        ▼
status ∈ {CONTAGIED, SUSPECT, POTENTIAL} → ValidationResult(false, "RED",  "Acceso denegado")
status = cualquier otro                  → ValidationResult(true,  "GREEN", "Bienvenido al campus")
```

---

## Patrón 3 — Feature Toggle (módulo Analytics)

### Contexto

El **dashboard-service** (`puerto 8084`) expone cinco endpoints de analytics bajo `/api/v1/analytics`. El Product Owner necesita desactivar el módulo completo en producción sin rebuild ni redeploy.

### Mecanismo

```
ConfigMap circleguard-config
  ANALYTICS_ENABLED: "true" / "false"
          │
          │  (variable de entorno inyectada al arrancar el pod)
          ▼
dashboard-service pod
  application.yml: analytics.enabled: ${ANALYTICS_ENABLED:true}
          │
          │  (binding con Spring @Value)
          ▼
AnalyticsController
  @Value("${analytics.enabled:true}") boolean analyticsEnabled
          │
          ├─ true  ──► delega a AnalyticsService → 200 OK
          └─ false ──► retorna 503 SERVICE_UNAVAILABLE inmediatamente
```

No se requieren cambios de código, rebuild de imagen, ni modificación de archivos de configuración. Solo el valor del ConfigMap y un reinicio del pod.

### Configuración

**`k8s/configmap.yaml`**
```yaml
data:
  ANALYTICS_ENABLED: "true"   # cambiar a "false" para desactivar
```

**`AnalyticsController.java`**
```java
@Value("${analytics.enabled:true}")
private boolean analyticsEnabled;

@GetMapping("/summary")
public ResponseEntity<Map<String, Object>> getSummary() {
    if (!analyticsEnabled) return disabled();
    return ResponseEntity.ok(analyticsService.getCampusSummary());
}

private <T> ResponseEntity<T> disabled() {
    Map<String, String> body = Map.of(
        "error", "Analytics module is currently disabled",
        "code", "ANALYTICS_DISABLED"
    );
    return (ResponseEntity<T>) ResponseEntity.status(503).body(body);
}
```

Los 5 endpoints (`/trends/{id}`, `/health-board`, `/summary`, `/department/{dept}`, `/time-series`) están protegidos por el mismo check.

---

## Patrón 4 — External Configuration

Toda la configuración de los 8 microservicios se externaliza al ConfigMap `circleguard-config` de Kubernetes. Las imágenes Docker no contienen valores hardcodeados — son inmutables y reutilizables entre ambientes.

```yaml
# k8s/{svc}-service/deployment.yaml
envFrom:
  - configMapRef:
      name: circleguard-config
  - secretRef:
      name: circleguard-secrets
```

Las 16+ variables incluyen hosts de BD, puertos, URLs de servicios, configuración de observabilidad (Zipkin, Prometheus) y parámetros de Kafka. Para rotar credenciales, basta actualizar el Secret y reiniciar los deployments — sin rebuilds.

---

## Demo — Observar los patrones en el cluster

### Prerequisitos
```bash
kubectl config set-context --current --namespace=dev
```

### Demo A — Retry seguido de Fallback

1. Escalar `promotion-service` a 0 réplicas:
   ```bash
   kubectl scale deployment promotion-service --replicas=0
   ```
2. Enviar una solicitud de validación:
   ```bash
   curl -sk -X POST https://gateway-dev.circleguard.local/api/v1/gate/validate \
     -H "Content-Type: application/json" \
     -d '{"token": "<valid-qr-token>"}'
   ```
3. Ver los logs del pod de gateway — se registran 3 reintentos y luego la advertencia del fallback:
   ```
   WARN  PromotionClient - promotion-service unavailable for anonymousId=..., using Redis cache.
   ```

### Demo B — Circuit Breaker abierto

1. Verificar el estado del circuito vía Actuator:
   ```bash
   kubectl exec -it deploy/gateway-service -- \
     curl -s localhost:8087/actuator/circuitbreakers | jq .
   ```
   Buscar `"state": "OPEN"` en `promotionService`.

2. Con el circuito OPEN, los logs del gateway ya no muestran reintentos — el fallback se activa inmediatamente.

3. Restaurar `promotion-service`:
   ```bash
   kubectl scale deployment promotion-service --replicas=1
   ```
   Después de 10s (`waitDurationInOpenState`), el CB pasa a HALF-OPEN y se permiten 3 llamadas de sondeo. Si tienen éxito, vuelve a CLOSED.

### Demo C — Ejecutar el test de resiliencia

```bash
cd services/circleguard-gateway-service
../../gradlew test --tests "com.circleguard.gateway.client.PromotionClientResilienceTest"
```

El test usa Testcontainers (Redis) + `MockRestServiceServer` (simula `promotion-service` retornando 500) para verificar:
- Exactamente 3 llamadas HTTP se realizan (Retry)
- El fallback retorna el valor cacheado en Redis
- Cuando el CB está manualmente abierto, 0 llamadas HTTP se realizan

---

## Archivos relevantes

| Archivo | Cambio |
|---------|--------|
| `services/circleguard-gateway-service/build.gradle.kts` | Dependencias `resilience4j-spring-boot3:2.2.0`, `spring-boot-starter-aop` |
| `services/circleguard-gateway-service/src/main/resources/application.yml` | Config de `services.promotion-url` y bloques `resilience4j` |
| `services/circleguard-gateway-service/src/main/java/.../client/PromotionClient.java` | **Nuevo** — cliente HTTP con `@CircuitBreaker` + `@Retry` + fallback Redis |
| `services/circleguard-gateway-service/src/main/java/.../config/RestTemplateConfig.java` | **Nuevo** — bean `RestTemplate` con timeouts |
| `services/circleguard-gateway-service/src/test/java/.../client/PromotionClientResilienceTest.java` | **Nuevo** — test de integración que demuestra ambos patrones |
| `services/circleguard-dashboard-service/src/main/java/.../controller/AnalyticsController.java` | Toggle `@Value` + guard `disabled()` en los 5 endpoints |
| `services/circleguard-dashboard-service/src/test/java/.../controller/AnalyticsFeatureToggleTest.java` | **Nuevo** — 5 tests que verifican 503 con el toggle desactivado |
| `k8s/configmap.yaml` | Variables `ANALYTICS_ENABLED` y configuración externalizada |
