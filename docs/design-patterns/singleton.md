# Patrón Singleton — Componentes de Infraestructura de Instancia Única

## Propósito

Garantizar que ciertos objetos costosos de construir (conexiones a bases de datos, clientes HTTP, templates de mensajería) se instancien exactamente una vez por contexto de aplicación, y que esa única instancia sea compartida por todos los componentes que la necesitan.

---

## Cómo funciona en CircleGuard

CircleGuard usa **Spring Framework** como contenedor de inversión de control. El `ApplicationContext` de Spring implementa el patrón Singleton de forma automática: todos los beans declarados con `@Bean`, `@Service`, `@Component`, `@Repository` o `@Controller` son Singletons por defecto (scope `singleton`).

### Ciclo de vida de un bean Singleton

```
Arranque de la aplicación (Spring Boot)
      │
      │  Spring escanea los paquetes (@ComponentScan)
      │  Procesa clases @Configuration
      ▼
ApplicationContext construye los beans en orden de dependencias
      │
      │  Cada bean se instancia UNA sola vez
      ▼
Inyección de dependencias (@Autowired / constructor injection)
      │
      │  Todos los componentes comparten la MISMA instancia
      ▼
Servicio en ejecución — cada request HTTP usa los beans ya creados
```

---

## Singletons de infraestructura en CircleGuard

### `RestTemplate` — cliente HTTP del gateway

```java
// gateway-service/src/main/java/com/circleguard/gateway/config/RestTemplateConfig.java
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        return new RestTemplate(factory);
    }
}
```

Un único `RestTemplate` con timeouts configurados es compartido por todas las llamadas de `PromotionClient` al `promotion-service`. Sin Singleton, cada request HTTP crearía un nuevo cliente con sus propios recursos de red.

---

### `KafkaTemplate` — productor de mensajes

```java
// form-service — inyección en HealthSurveyService
@Service
@RequiredArgsConstructor
public class HealthSurveyService {
    private final KafkaTemplate<String, Object> kafkaTemplate;  // singleton
    // ...
}
```

Spring Boot auto-configura un único `KafkaTemplate` a partir de las propiedades `spring.kafka.*`. Todos los servicios que publican eventos (`form-service`, `promotion-service`) comparten la misma instancia del productor Kafka, que internamente mantiene un pool de conexiones al broker.

---

### `StringRedisTemplate` — caché Redis

```java
// gateway-service — PromotionClient
@Service
@RequiredArgsConstructor
public class PromotionClient {
    private final StringRedisTemplate redisTemplate;  // singleton
    // ...
}
```

Un único `StringRedisTemplate` gestiona todas las operaciones de lectura y escritura sobre Redis. Internamente mantiene un pool de conexiones (`LettuceConnectionFactory`) que se reutiliza en cada request.

---

### `@Service`, `@Component`, `@Repository` — Singletons implícitos

Todos los servicios y repositorios del sistema son Singletons automáticamente:

```java
@Service
@RequiredArgsConstructor
public class HealthSurveyService { ... }    // singleton

@Component
public class SymptomMapper { ... }          // singleton

@Service
public class ExposureNotificationListener { ... }  // singleton

public interface QuestionnaireRepository
    extends JpaRepository<Questionnaire, UUID> { }  // implementación singleton generada por Spring Data
```

Spring instancia cada uno una sola vez al arrancar la aplicación y los inyecta por constructor (`@RequiredArgsConstructor` de Lombok genera el constructor con todos los campos `final`).

---

### `@Configuration` — fábricas Singleton

Las clases de configuración son Singletons especiales: sus métodos `@Bean` son interceptados por Spring para garantizar que aunque un `@Bean` llame a otro `@Bean`, siempre se devuelve la misma instancia:

```java
@Configuration
public class DataLayerConfig {
    // Cada @Bean se instancia una sola vez, incluso si se invoca desde otro @Bean
    @Bean
    public SomeDependency someDependency() { return new SomeDependency(); }
}
```

---

## Singletons por servicio

| Servicio | Singletons clave | Propósito |
|----------|-----------------|-----------|
| `gateway-service` | `RestTemplate`, `StringRedisTemplate`, `PromotionClient` | HTTP al promotion-service, caché QR en Redis |
| `form-service` | `KafkaTemplate`, `HealthSurveyService`, `SymptomMapper` | Publicación de eventos, lógica de encuestas |
| `promotion-service` | `HealthStatusService`, `SurveyListener`, `KafkaTemplate` | Grafo de estado, consumo de eventos |
| `notification-service` | `ExposureNotificationListener`, `NotificationDispatcher` | Despacho de notificaciones |
| Todos | `JpaRepository` (implementaciones) | Acceso a PostgreSQL |

---

## Beneficios

| Beneficio | Descripción |
|-----------|-------------|
| **Eficiencia de recursos** | Un pool de conexiones a PostgreSQL o Redis se crea una vez y se reutiliza. Sin Singleton, cada request abriría y cerraría una conexión nueva. |
| **Estado compartido seguro** | `KafkaTemplate` y `RestTemplate` son thread-safe y diseñados para ser Singletons — no hay condiciones de carrera. |
| **Coherencia de configuración** | Los timeouts, URLs y credenciales se configuran una vez en el `@Configuration` y aplican uniformemente a todas las llamadas. |
| **Bajo costo de inyección** | Spring resuelve la inyección en el arranque, no en cada request. El overhead en tiempo de ejecución es mínimo. |
| **Testabilidad** | Los Singletons de Spring se pueden reemplazar con mocks en tests usando `@MockBean`, sin modificar el código de producción. |

---

## Diferencia con Singleton manual (anti-patrón)

CircleGuard no usa el patrón Singleton "clásico" con campo `static INSTANCE` y constructor privado, que dificulta el testing. En cambio, delega la gestión del ciclo de vida al contenedor de Spring, que provee las mismas garantías de instancia única con soporte nativo para inyección y reemplazo en tests.

```java
// NO se usa este estilo (Singleton manual):
public class PromotionClient {
    private static final PromotionClient INSTANCE = new PromotionClient();
    private PromotionClient() {}
    public static PromotionClient getInstance() { return INSTANCE; }
}

// SÍ se usa este estilo (Singleton gestionado por Spring):
@Service
@RequiredArgsConstructor
public class PromotionClient {
    private final RestTemplate restTemplate;  // inyectado, mockeable en tests
}
```

---

## Archivos relevantes

| Archivo | Bean Singleton definido |
|---------|------------------------|
| `services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/config/RestTemplateConfig.java` | `RestTemplate` con timeouts |
| `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/config/CacheConfig.java` | Configuración de Redis cache |
| `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/config/DataLayerConfig.java` | Configuración de la capa de datos dual (JPA + Neo4j) |
| `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/config/Neo4jConfig.java` | Driver de Neo4j |
