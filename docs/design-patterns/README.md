# Patrones de Diseño — CircleGuard

CircleGuard implementa 8 patrones de diseño distribuidos entre los 8 microservicios. Cada archivo documenta el propósito, la implementación concreta en el código, y los beneficios de cada patrón.

---

## Índice de patrones

### Patrones arquitecturales

| Patrón | Archivo | Ubicación en el código |
|--------|---------|----------------------|
| **Observer** | [observer.md](observer.md) | Kafka: `form-service` → `promotion-service` → `notification-service` |
| **API Gateway** | [api-gateway.md](api-gateway.md) | `circleguard-gateway-service` — JWT validation, Redis cache, Circuit Breaker |

### Patrones de acceso a datos

| Patrón | Archivo | Ubicación en el código |
|--------|---------|----------------------|
| **Repository** | [repository.md](repository.md) | `IdentityMappingRepository`, `QuestionnaireRepository`, `HealthSurveyRepository`, `LocalUserRepository` (Spring Data JPA) |
| **DTO / Mapper** | [dto-mapper.md](dto-mapper.md) | `SymptomMapper`, paquetes `dto/` en cada servicio |

### Patrones de creación

| Patrón | Archivo | Ubicación en el código |
|--------|---------|----------------------|
| **Singleton** | [singleton.md](singleton.md) | Spring `@Bean`, `@Service`, `@Component` — `ApplicationContext` |

### Patrones de resiliencia y configuración

| Patrón | Archivo | Ubicación en el código |
|--------|---------|----------------------|
| **Circuit Breaker + Retry** | [resilience.md](resilience.md) | `PromotionClient` en `gateway-service` — Resilience4j |
| **Feature Toggle** | [resilience.md](resilience.md) | `AnalyticsController` en `dashboard-service` — variable `ANALYTICS_ENABLED` |
| **External Configuration** | [resilience.md](resilience.md) | ConfigMap `circleguard-config` — 16 variables de ambiente inyectadas vía `envFrom` |

---

## Descripción resumida

### Observer
Kafka actúa como broker de eventos de dominio. `form-service` publica eventos (`survey.submitted`, `certificate.validated`) que `promotion-service` consume para actualizar el estado de salud en Neo4j. A su vez, `promotion-service` publica en `promotion.status.changed` para que `notification-service` despache alertas. Ningún servicio conoce a sus suscriptores.

### API Gateway
`circleguard-gateway-service` es el único punto de entrada público al sistema. Valida tokens JWT de QR, consulta el estado de salud con resiliencia (Circuit Breaker + Retry + Redis fallback) y decide si el acceso al campus está permitido (`GREEN`) o denegado (`RED`).

### Repository
Spring Data JPA genera automáticamente las implementaciones de acceso a datos a partir de interfaces. Los servicios hablan con interfaces Repository sin conocer SQL ni JPQL.

### DTO / Mapper
`SymptomMapper` traduce respuestas de encuesta a un booleano de negocio. Los paquetes `dto/` de cada servicio definen el contrato JSON de las APIs, desacoplado del modelo de dominio interno.

### Singleton
Todos los beans de Spring (`@Service`, `@Component`, `@Bean`) son Singletons por defecto. Clientes HTTP (`RestTemplate`), productores Kafka (`KafkaTemplate`) y clientes Redis (`StringRedisTemplate`) se instancian una vez y se reutilizan en todos los requests.

### Circuit Breaker + Retry
Resilience4j protege la llamada del `gateway-service` al `promotion-service`. 3 reintentos con backoff exponencial (500ms → 1s → 2s) y Circuit Breaker que abre cuando la tasa de fallos supera el 50% en una ventana de 10 llamadas.

### Feature Toggle
La variable `ANALYTICS_ENABLED` en el ConfigMap de Kubernetes controla si `AnalyticsController` procesa requests o retorna HTTP 503 inmediatamente. Permite desactivar el módulo de analytics sin rebuild ni redeploy.

### External Configuration
Toda la configuración de los 8 microservicios (hosts de BD, puertos, URLs, secrets) se externaliza al ConfigMap `circleguard-config` y al Secret `circleguard-secrets`. Las imágenes Docker son inmutables y reutilizables entre ambientes.
