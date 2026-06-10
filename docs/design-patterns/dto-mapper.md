# Patrón DTO / Mapper — Separación entre Dominio y Contrato de API

## Propósito

Evitar que el modelo de dominio (entidades JPA, grafos Neo4j) se filtre directamente hacia los contratos de API. Un DTO define exactamente qué datos se exponen en la respuesta HTTP; un Mapper traduce entre el modelo interno y el DTO de forma explícita y testeable.

---

## Cómo funciona en CircleGuard

### Problema sin DTOs

Sin este patrón, exponer `HealthSurvey` directamente en un `@RestController` tendría estas consecuencias:
- Cambios internos al modelo de dominio rompen el contrato de la API.
- Campos sensibles (IDs internos, hashes de identidad, claves de auditoría) se serializan accidentalmente en las respuestas.
- Los tests de serialización se acoplan a los detalles de la base de datos.

### Solución aplicada

```
Cliente HTTP
    │
    │  JSON request/response
    ▼
Controller
    │  usa DTOs (clases de entrada/salida de API)
    ▼
Mapper (traduce entre DTO ↔ modelo de dominio)
    │
    ▼
Service / Repository (trabaja solo con modelos de dominio)
```

---

## Implementaciones en el código

### `SymptomMapper` (form-service)

Traduce las respuestas de una encuesta de salud a un booleano de negocio (`hasSymptoms`). Encapsula la lógica de interpretación de síntomas — que varía según el cuestionario activo — sin que el servicio deba conocer los detalles de los tipos de pregunta.

```java
// form-service/src/main/java/com/circleguard/form/service/SymptomMapper.java
@Component
public class SymptomMapper {

    public boolean hasSymptoms(HealthSurvey survey, Questionnaire questionnaire) {
        Map<String, Object> responses = survey.getResponses();

        return questionnaire.getQuestions().stream()
            .filter(q -> responses.containsKey(q.getId().toString()))
            .anyMatch(q -> {
                String answer = String.valueOf(responses.get(q.getId().toString()));
                String text = q.getText().toLowerCase();

                if ("YES".equalsIgnoreCase(answer)) {
                    return text.contains("fever") || text.contains("cough") || text.contains("breathing");
                }
                if (q.getType().toString().contains("CHOICE") && answer != null && !answer.isEmpty()) {
                    return text.contains("symptoms");
                }
                return false;
            });
    }
}
```

**Uso en el servicio:**

```java
// HealthSurveyService.java
boolean hasSymptoms = activeQuestionnaire
    .map(q -> symptomMapper.hasSymptoms(survey, q))
    .orElseGet(() -> checkLegacyKeys(survey.getResponses()));
```

El servicio delega completamente la interpretación de respuestas al mapper. Si la lógica de detección de síntomas cambia (nuevas palabras clave, nueva estructura de respuestas), solo se modifica `SymptomMapper`.

---

### DTOs de `promotion-service`

```java
// promotion-service/src/main/java/com/circleguard/promotion/dto/BuildingDTO.java
// promotion-service/src/main/java/com/circleguard/promotion/dto/FloorDTO.java
// promotion-service/src/main/java/com/circleguard/promotion/dto/AccessPointDTO.java
```

Los controladores de `promotion-service` devuelven DTOs en lugar de entidades JPA directas. Esto separa la representación del campus (edificios, pisos, puntos de acceso) del modelo interno gestionado por Neo4j y PostgreSQL.

---

### Eventos Kafka como DTOs de mensajería

Los eventos publicados en Kafka también siguen el principio de DTO: son `Map<String, Object>` con estructura definida explícitamente, no objetos de dominio serializados directamente.

```java
// form-service — publicación
Map<String, Object> event = Map.of(
    "anonymousId", saved.getAnonymousId(),   // solo el ID anónimo, no la entidad completa
    "hasSymptoms", hasSymptoms,
    "timestamp", System.currentTimeMillis()
);
kafkaTemplate.send("survey.submitted", saved.getAnonymousId().toString(), event);
```

```java
// promotion-service — consumo
String anonymousId = (String) event.get("anonymousId");
Boolean hasSymptoms = (Boolean) event.get("hasSymptoms");
```

Este contrato explícito permite evolucionar la entidad `HealthSurvey` sin romper el consumidor Kafka.

---

## Estructura de paquetes `dto/`

Cada servicio con API REST tiene un paquete `dto/` dedicado:

```
circleguard-promotion-service/
└── src/main/java/com/circleguard/promotion/
    ├── dto/
    │   ├── AccessPointDTO.java
    │   ├── BuildingDTO.java
    │   └── FloorDTO.java
    ├── model/          ← entidades JPA / Neo4j (nunca salen del servicio)
    └── controller/     ← usa DTOs, nunca expone modelos directamente
```

---

## Beneficios

| Beneficio | Descripción |
|-----------|-------------|
| **Contrato de API estable** | El modelo de dominio puede evolucionar (renombrar campos, agregar columnas) sin cambiar la respuesta JSON que ven los clientes. |
| **Ocultamiento de datos internos** | IDs de base de datos, timestamps de auditoría, hashes de identidad no se serializan accidentalmente. |
| **Testabilidad del mapping** | `SymptomMapper` tiene tests unitarios propios (`SymptomMapperTest.java`) con escenarios de YES/NO, preguntas de opción múltiple y cuestionario nulo. |
| **Validación en frontera** | Los DTOs de entrada pueden tener anotaciones `@NotNull`, `@Size` etc., centralizando la validación de entrada sin contaminar el modelo de dominio. |
| **Privacidad** | El `anonymousId` circula entre servicios vía DTOs/eventos, nunca la identidad real del usuario — el Privacy Vault de `identity-service` es el único que conoce la correspondencia. |

---

## Archivos relevantes

| Archivo | Rol |
|---------|-----|
| `services/circleguard-form-service/src/main/java/com/circleguard/form/service/SymptomMapper.java` | Mapea respuestas de encuesta → booleano de síntomas |
| `services/circleguard-form-service/src/test/java/com/circleguard/form/service/SymptomMapperTest.java` | Tests unitarios del mapper |
| `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/dto/BuildingDTO.java` | DTO de edificio del campus |
| `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/dto/FloorDTO.java` | DTO de piso del campus |
| `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/dto/AccessPointDTO.java` | DTO de punto de acceso QR |
