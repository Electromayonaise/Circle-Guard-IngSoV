# Patrón Repository — Abstracción de Acceso a Datos

## Propósito

Aislar la lógica de negocio del mecanismo de persistencia, proporcionando una interfaz orientada a colecciones para acceder a objetos de dominio. Los servicios hablan con repositorios, no con tablas ni queries SQL.

---

## Cómo funciona en CircleGuard

CircleGuard implementa el patrón Repository usando **Spring Data JPA**. Cada entidad de dominio tiene su propia interfaz que extiende `JpaRepository<Entity, ID>`. Spring genera automáticamente la implementación concreta en tiempo de ejecución a través de `SimpleJpaRepository`.

### Estructura general

```
Capa de Servicio
      │
      │  Habla solo con la interfaz Repository
      ▼
<<interface>> XxxRepository
  extends JpaRepository<Entity, ID>
      │
      │  Spring Data genera la implementación
      ▼
SimpleJpaRepository (Spring Data interno)
      │
      │  Traduce a JPQL / SQL
      ▼
Base de datos (PostgreSQL)
```

---

## Repositorios implementados

### `IdentityMappingRepository` (identity-service)

```java
// identity-service/src/main/java/com/circleguard/identity/repository/IdentityMappingRepository.java
public interface IdentityMappingRepository extends JpaRepository<IdentityMapping, UUID> {
    Optional<IdentityMapping> findByIdentityHash(String identityHash);
}
```

Gestiona el mapeo privacidad-preservante entre identidades reales y `anonymousId`. El método `findByIdentityHash` es un query derivado de Spring Data — genera automáticamente `SELECT * FROM identity_mapping WHERE identity_hash = ?`.

---

### `QuestionnaireRepository` (form-service)

```java
// form-service/src/main/java/com/circleguard/form/repository/QuestionnaireRepository.java
public interface QuestionnaireRepository extends JpaRepository<Questionnaire, UUID> {
    Optional<Questionnaire> findFirstByIsActiveTrueOrderByVersionDesc();
}
```

Obtiene el cuestionario activo de mayor versión. La convención de nombres de Spring Data traduce esto a `SELECT * FROM questionnaire WHERE is_active = true ORDER BY version DESC LIMIT 1` — sin escribir SQL manualmente.

---

### `HealthSurveyRepository` (form-service)

```java
// form-service/src/main/java/com/circleguard/form/repository/HealthSurveyRepository.java
public interface HealthSurveyRepository extends JpaRepository<HealthSurvey, UUID> { }
```

Operaciones CRUD completas sobre encuestas de salud. Las operaciones básicas (`save`, `findById`, `findAll`, `delete`) son heredadas de `JpaRepository` sin código adicional.

---

### `QuestionRepository` (form-service)

```java
// form-service/src/main/java/com/circleguard/form/repository/QuestionRepository.java
public interface QuestionRepository extends JpaRepository<Question, UUID> { }
```

Repositorio de preguntas individuales dentro de un cuestionario.

---

### `LocalUserRepository` (auth-service)

```java
// auth-service/src/main/java/com/circleguard/auth/repository/LocalUserRepository.java
public interface LocalUserRepository extends JpaRepository<LocalUser, UUID> { }
```

Gestiona usuarios locales para autenticación JWT.

---

### Repositorios de `promotion-service` (JPA + Neo4j)

```java
// promotion-service/src/main/java/com/circleguard/promotion/repository/jpa/AccessPointRepository.java
public interface AccessPointRepository extends JpaRepository<AccessPoint, UUID> { }

// promotion-service/.../BuildingRepository.java
public interface BuildingRepository extends JpaRepository<Building, UUID> { }

// promotion-service/.../FloorRepository.java
public interface FloorRepository extends JpaRepository<Floor, UUID> { }

// promotion-service/.../SystemSettingsRepository.java
public interface SystemSettingsRepository extends JpaRepository<SystemSettings, UUID> { }
```

`promotion-service` usa dos capas de persistencia: JPA (PostgreSQL) para la configuración del campus (edificios, pisos, puntos de acceso) y **Spring Data Neo4j** para el grafo de estados de salud (relaciones de contacto y propagación).

---

## Uso en la capa de servicio

Los servicios reciben los repositorios por inyección de dependencias y los usan directamente sin conocer SQL:

```java
@Service
@RequiredArgsConstructor
public class HealthSurveyService {
    private final HealthSurveyRepository repository;   // inyectado automáticamente
    private final QuestionnaireService questionnaireService;

    @Transactional
    public HealthSurvey submitSurvey(HealthSurvey survey) {
        // ...lógica de negocio...
        HealthSurvey saved = repository.save(survey);  // delega al repositorio
        return saved;
    }
}
```

---

## Beneficios

| Beneficio | Descripción |
|-----------|-------------|
| **Separación de responsabilidades** | Los servicios de negocio no contienen queries SQL ni lógica de persistencia. |
| **Testabilidad** | Se puede mockear `HealthSurveyRepository` con Mockito sin necesitar base de datos. Los tests unitarios son rápidos y deterministas. |
| **Queries declarativos** | Métodos como `findFirstByIsActiveTrueOrderByVersionDesc` generan SQL automáticamente por convención de nombres, sin código de infraestructura. |
| **Transacciones declarativas** | `@Transactional` en el servicio garantiza atomicidad sin SQL manual de `BEGIN`/`COMMIT`. |
| **Intercambiabilidad** | Cambiar de PostgreSQL a otro motor SQL solo requiere ajustar la configuración — la interfaz del repositorio no cambia. |

---

## Archivos relevantes

| Archivo | Entidad gestionada |
|---------|--------------------|
| `services/circleguard-identity-service/src/main/java/com/circleguard/identity/repository/IdentityMappingRepository.java` | `IdentityMapping` (vault de identidades) |
| `services/circleguard-form-service/src/main/java/com/circleguard/form/repository/QuestionnaireRepository.java` | `Questionnaire` (formularios activos) |
| `services/circleguard-form-service/src/main/java/com/circleguard/form/repository/HealthSurveyRepository.java` | `HealthSurvey` (encuestas enviadas) |
| `services/circleguard-form-service/src/main/java/com/circleguard/form/repository/QuestionRepository.java` | `Question` (preguntas de cuestionarios) |
| `services/circleguard-auth-service/src/main/java/com/circleguard/auth/repository/LocalUserRepository.java` | `LocalUser` (usuarios de autenticación) |
| `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/repository/jpa/` | `AccessPoint`, `Building`, `Floor`, `SystemSettings` |
