# Patrón API Gateway — Punto de Entrada Único con Cross-Cutting Concerns

## Propósito

Centralizar en un único servicio todas las preocupaciones transversales (autenticación, autorización, caché, resiliencia, enrutamiento) para que los microservicios de backend solo implementen lógica de dominio. Los clientes externos interactúan con un único punto de entrada en lugar de comunicarse directamente con cada servicio.

---

## Cómo funciona en CircleGuard

`circleguard-gateway-service` (puerto 8087) es el único servicio expuesto públicamente a través del Ingress de Kubernetes. Todos los dispositivos de control de acceso y clientes frontend pasan por este servicio.

### Responsabilidades del Gateway

```
Cliente (dispositivo de acceso / frontend)
      │
      │  POST /api/v1/gate/validate  { "token": "<QR JWT>" }
      ▼
┌─────────────────────────────────────────────┐
│          circleguard-gateway-service         │
│                                             │
│  1. Validación JWT del token QR             │
│     → verifica firma, expiración, claims    │
│                                             │
│  2. Extrae anonymousId del JWT              │
│                                             │
│  3. Consulta estado de salud               │
│     → llama a promotion-service (con CB+Retry) │
│     → fallback: Redis cache                 │
│                                             │
│  4. Decisión de acceso                      │
│     CONTAGIED / SUSPECT → DENY (RED)        │
│     cualquier otro       → ALLOW (GREEN)    │
│                                             │
│  5. Responde al dispositivo                 │
└─────────────────────────────────────────────┘
      │
      │  No llega nunca al microservicio de backend directamente
      ▼
promotion-service (solo accesible internamente en el cluster)
```

---

## Implementación del flujo de validación

### `QrValidationService` — orquestador del gateway

```java
// gateway-service/src/main/java/com/circleguard/gateway/service/QrValidationService.java
public ValidationResult validateToken(String token) {
    // 1. Valida JWT y extrae anonymousId
    String anonymousId = jwtParser.extractAnonymousId(token);

    // 2. Consulta estado con resiliencia (Circuit Breaker + Retry)
    String status = promotionClient.getHealthStatus(anonymousId);

    // 3. Decisión de acceso basada en estado
    boolean allow = !Set.of("CONTAGIED", "SUSPECT", "POTENTIAL").contains(status);
    String color = allow ? "GREEN" : "RED";
    String message = allow ? "Welcome to Campus" : "Access Denied";

    return new ValidationResult(allow, color, message);
}
```

### `PromotionClient` — cliente resiliente con caché

```java
// gateway-service/src/main/java/com/circleguard/gateway/client/PromotionClient.java
@Service
@RequiredArgsConstructor
public class PromotionClient {

    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;

    @CircuitBreaker(name = "promotionService", fallbackMethod = "getStatusFromCache")
    @Retry(name = "promotionService")
    public String getHealthStatus(String anonymousId) {
        String url = promotionServiceUrl + "/api/v1/health/status/" + anonymousId;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(buildServiceToken());  // JWT service-to-service
        ResponseEntity<Map> response = restTemplate.exchange(
            url, HttpMethod.GET, new HttpEntity<>(headers), Map.class
        );
        Map<?, ?> body = response.getBody();
        return body != null ? (String) body.get("status") : "UNKNOWN";
    }

    String getStatusFromCache(String anonymousId, Exception ex) {
        log.warn("promotion-service unavailable, using Redis cache. anonymousId={}", anonymousId);
        String cached = redisTemplate.opsForValue().get("user:status:" + anonymousId);
        return cached != null ? cached : "UNKNOWN";
    }
}
```

---

## Cross-cutting concerns centralizados

### 1. Autenticación JWT

El gateway valida todos los tokens QR antes de procesar cualquier solicitud. Los microservicios de backend no implementan validación de tokens de acceso al campus — confían en que si la solicitud llegó al servicio interno, ya fue autenticada en el gateway.

```
Token QR = JWT firmado con { anonymousId, issuedAt, expiresAt }
Clave de firma: inyectada desde circleguard-secrets (Kubernetes Secret)
```

### 2. Caché de estado en Redis

El estado de salud de cada usuario se cachea en Redis con clave `user:status:{anonymousId}`. Esto sirve tanto como acelerador de consultas como fallback cuando `promotion-service` no está disponible.

```
Redis key:   user:status:<anonymousId>
Redis value: "ACTIVE" | "SUSPECT" | "CONTAGIED" | "POTENTIAL" | "CLEARED"
Escritura:   promotion-service escribe al cambiar el estado
Lectura:     gateway-service lee en el fallback del Circuit Breaker
```

### 3. Resiliencia (Circuit Breaker + Retry)

El gateway absorbe fallos transitorios del `promotion-service` sin propagar errores al dispositivo de acceso:

```yaml
# application.yml — gateway-service
resilience4j:
  retry:
    instances:
      promotionService:
        maxAttempts: 3
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
  circuitbreaker:
    instances:
      promotionService:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 3
```

### 4. Autorización service-to-service

El gateway firma sus propias llamadas a `promotion-service` con un JWT de servicio (subject: `gateway-service`, role: `ROLE_SERVICE`). Los microservicios pueden verificar que las llamadas vienen del gateway autorizado.

```java
private String buildServiceToken() {
    Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
    return Jwts.builder()
        .setSubject("gateway-service")
        .claim("permissions", List.of("ROLE_SERVICE"))
        .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
}
```

### 5. TLS y HTTPS

El Ingress de Kubernetes apunta al gateway con TLS configurado mediante cert-manager. La terminación TLS ocurre en el Ingress controller, y el gateway sirve el tráfico interno en HTTP plain dentro del cluster.

```yaml
# k8s/cert-manager/ingress.yaml
spec:
  tls:
    - hosts: [gateway-dev.circleguard.local]
      secretName: gateway-tls-secret
  rules:
    - host: gateway-dev.circleguard.local
      http:
        paths:
          - path: /
            backend:
              service:
                name: gateway-service
                port: 8087
```

---

## Diagrama de topología

```
Internet / Red del campus
      │
      │  HTTPS (TLS terminado en Ingress)
      ▼
Ingress Controller (ingress-nginx)
      │
      │  HTTP interno
      ▼
gateway-service :8087
  ├── Valida JWT
  ├── Redis (caché QR)
  ├── Circuit Breaker → promotion-service :8088
  └── Respuesta de acceso
                         │
                         │  Solo tráfico interno del cluster
                         ▼
                  promotion-service
                  identity-service
                  form-service
                  notification-service
                  dashboard-service
                  file-service
                  auth-service
```

Ningún microservicio de backend tiene IP pública ni Ingress propio. El gateway es el único punto de entrada al sistema desde redes externas.

---

## Beneficios

| Beneficio | Descripción |
|-----------|-------------|
| **Punto único de seguridad** | JWT validation, TLS y RBAC de red se configuran en un solo lugar. Agregar un nuevo microservicio no requiere reconfigurar seguridad de red. |
| **Superficie de ataque reducida** | Los microservicios de backend no son accesibles desde internet. Un atacante que descubra la IP de `promotion-service` no puede acceder a él directamente. |
| **Consistencia de respuesta** | Todos los dispositivos de acceso reciben la misma estructura de respuesta `{ allow, color, message }` independientemente de cómo evolucione el backend. |
| **Degradación elegante** | Si `promotion-service` falla, el gateway sigue funcionando usando la caché Redis, sin bloquear el acceso al campus. |
| **Observabilidad centralizada** | Los logs de acceso (quién intentó entrar, con qué estado, en qué momento) se registran en un solo servicio y son consultables desde Grafana Loki. |

---

## Archivos relevantes

| Archivo | Contenido |
|---------|-----------|
| `services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/client/PromotionClient.java` | Cliente HTTP resiliente con Circuit Breaker + Retry + Redis fallback |
| `services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/config/RestTemplateConfig.java` | Bean `RestTemplate` con timeouts |
| `services/circleguard-gateway-service/src/main/resources/application.yml` | Configuración Resilience4j |
| `k8s/cert-manager/` | TLS con cert-manager |
| `k8s/circleguard-gateway-service/ingress.yaml` | Ingress con HTTPS y force-ssl-redirect |
