# Patrón Observer — Eventos de Dominio vía Kafka

## Propósito

Desacoplar los productores de eventos de negocio de sus consumidores, permitiendo que múltiples servicios reaccionen de forma independiente y asíncrona a un mismo evento de dominio sin que el productor conozca a sus suscriptores.

---

## Cómo funciona en CircleGuard

CircleGuard implementa el patrón Observer usando **Apache Kafka** como bus de mensajería. Hay dos flujos principales:

### Flujo 1 — Encuesta de salud enviada

```
form-service (Publisher)
      │
      │  KafkaTemplate.send("survey.submitted", event)
      │  payload: { anonymousId, hasSymptoms, timestamp }
      ▼
 Kafka topic: survey.submitted
      │
      ▼
promotion-service (Observer)
  SurveyListener.onSurveySubmitted()
  → si hasSymptoms == true → updateStatus(anonymousId, "SUSPECT")
```

**Productor — `HealthSurveyService` (form-service)**

```java
// form-service/src/main/java/com/circleguard/form/service/HealthSurveyService.java
Map<String, Object> event = Map.of(
    "anonymousId", saved.getAnonymousId(),
    "hasSymptoms", hasSymptoms,
    "timestamp", System.currentTimeMillis()
);
kafkaTemplate.send(TOPIC_SURVEY_SUBMITTED, saved.getAnonymousId().toString(), event);
```

**Consumidor — `SurveyListener` (promotion-service)**

```java
// promotion-service/src/main/java/com/circleguard/promotion/listener/SurveyListener.java
@KafkaListener(topics = "survey.submitted", groupId = "promotion-service-group")
public void onSurveySubmitted(Map<String, Object> event) {
    String anonymousId = (String) event.get("anonymousId");
    Boolean hasSymptoms = (Boolean) event.get("hasSymptoms");
    if (anonymousId != null && Boolean.TRUE.equals(hasSymptoms)) {
        healthStatusService.updateStatus(anonymousId, "SUSPECT");
    }
}
```

---

### Flujo 2 — Cambio de estado de salud

```
promotion-service (Publisher)
      │
      │  Publica en "promotion.status.changed"
      │  payload: { anonymousId, status }
      ▼
 Kafka topic: promotion.status.changed
      │
      ▼
notification-service (Observer)
  ExposureNotificationListener.handleStatusChange()
  → dispatcher.dispatch(userId, status)
  → lmsService.syncRemoteAttendance(userId, status)
```

**Consumidor — `ExposureNotificationListener` (notification-service)**

```java
// notification-service/src/main/java/com/circleguard/notification/service/ExposureNotificationListener.java
@KafkaListener(topics = "promotion.status.changed", groupId = "notification-group")
public void handleStatusChange(String eventJson) {
    JsonNode node = objectMapper.readTree(eventJson);
    String userId = node.path("anonymousId").asText("unknown");
    String status = node.path("status").asText("UNKNOWN");
    if (!"ACTIVE".equals(status) && !"UNKNOWN".equals(status)) {
        dispatcher.dispatch(userId, status);
        lmsService.syncRemoteAttendance(userId, status);
    }
}
```

---

## Topics de Kafka utilizados

| Topic | Productor | Consumidor(es) | Payload |
|-------|-----------|----------------|---------|
| `survey.submitted` | `form-service` | `promotion-service` | `{ anonymousId, hasSymptoms, timestamp }` |
| `certificate.validated` | `form-service` | `promotion-service` | `{ anonymousId, status: "APPROVED"\|"REJECTED" }` |
| `promotion.status.changed` | `promotion-service` | `notification-service` | `{ anonymousId, status }` |

---

## Diagrama de flujo completo

```
┌─────────────┐   survey.submitted   ┌──────────────────┐  promotion.status.changed  ┌───────────────────────┐
│ form-service │ ──────────────────► │ promotion-service │ ─────────────────────────► │ notification-service  │
│             │                      │                  │                             │                       │
│  submitSurvey()                    │  SurveyListener  │                             │  ExposureNotification │
│  → KafkaTemplate.send()            │  updateStatus()  │                             │  Listener             │
└─────────────┘                      └──────────────────┘                             │  dispatcher.dispatch()│
                                                                                      └───────────────────────┘
```

---

## Beneficios

| Beneficio | Descripción |
|-----------|-------------|
| **Desacoplamiento** | `form-service` no conoce a `promotion-service` ni a `notification-service`. Agregar un nuevo consumidor solo requiere crear un nuevo `@KafkaListener` sin modificar el productor. |
| **Resiliencia** | Si `promotion-service` está caído, los eventos se acumulan en el topic de Kafka y se procesan cuando el servicio se recupera. No hay pérdida de datos. |
| **Escalabilidad** | Cada consumidor puede escalar horizontalmente de forma independiente. Kafka gestiona la distribución de mensajes mediante consumer groups. |
| **Auditoría** | Los mensajes persisten en Kafka con retención configurable, permitiendo reproducir eventos para debugging o auditoría. |
| **Trazabilidad** | Cada evento incluye `anonymousId` y `timestamp`, lo que permite construir una línea de tiempo del estado de salud de un usuario anónimo. |

---

## Diferencia con Observer síncrono

En lugar de llamadas HTTP directas entre servicios (acoplamiento temporal), el broker Kafka garantiza la entrega incluso si el consumidor no está disponible en el momento del evento. Esto hace al sistema tolerante a fallos de red y reinicios de pods sin necesidad de lógica de reintentos en el productor.

---

## Archivos relevantes

| Archivo | Rol |
|---------|-----|
| `services/circleguard-form-service/src/main/java/com/circleguard/form/service/HealthSurveyService.java` | Productor — publica `survey.submitted` y `certificate.validated` |
| `services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/listener/SurveyListener.java` | Consumidor — reacciona a encuestas y certificados |
| `services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/ExposureNotificationListener.java` | Consumidor — reacciona a cambios de estado |
| `services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/CircleFencedListener.java` | Consumidor — reacciona a eventos de cerco geográfico |
| `services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/PriorityAlertListener.java` | Consumidor — alertas prioritarias |
