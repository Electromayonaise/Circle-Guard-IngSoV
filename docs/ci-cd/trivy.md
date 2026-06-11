# Trivy — Escaneo de Vulnerabilidades en Contenedores

CircleGuard usa **Trivy** (Aqua Security) para detectar CVEs en las imágenes Docker de los 8 microservicios antes de que lleguen a producción. El escaneo corre automáticamente en los pipelines de stage y master, después de publicar las imágenes en el Azure Container Registry (ACR).

---

## Integración en el pipeline de Jenkins

```
Docker Build & Push → Trivy Security Scan → Deploy
```

Trivy escanea las imágenes directamente desde ACR usando el mismo token de autenticación que el stage de Docker Push — no requiere credenciales adicionales.

```groovy
// Jenkinsfile.stage / Jenkinsfile.master
stage('Trivy Security Scan') {
    steps {
        script {
            def services = [
                'auth-service', 'identity-service', 'form-service',
                'promotion-service', 'notification-service', 'gateway-service',
                'dashboard-service', 'file-service'
            ]
            def reportFile = 'trivy-stage-report.txt'
            def trivyOutput = ""
            def criticalFound = false

            services.each { svc ->
                def image = "circleguarddevacr.azurecr.io/${svc}:${VERSION}"
                def result = sh(
                    script: "trivy image --severity CRITICAL,HIGH --no-progress ${image}",
                    returnStdout: true
                )
                trivyOutput += "\n=== ${svc} ===\n${result}"

                if (result.contains('CRITICAL')) {
                    trivyOutput += "\n[FAIL] CRITICAL vulnerabilities found in ${svc}"
                    criticalFound = true
                } else {
                    trivyOutput += "\n[PASS] No CRITICAL vulnerabilities in ${svc}"
                }
            }

            writeFile file: reportFile, text: trivyOutput
            archiveArtifacts artifacts: reportFile

            if (criticalFound) {
                error("Trivy found CRITICAL vulnerabilities — pipeline blocked")
            }
        }
    }
}
```

---

## Política de severidad

| Severidad | Comportamiento |
|-----------|----------------|
| **CRITICAL** | Bloquea el pipeline — el deploy no ocurre |
| **HIGH** | Se reporta en el artefacto pero no bloquea |
| **MEDIUM / LOW / UNKNOWN** | No se reportan (fuera del scope del scan) |

---

## Reporte de Trivy

El reporte completo se archiva como artefacto de Jenkins en cada build:

- Pipeline stage: `trivy-stage-report.txt`
- Pipeline master: `trivy-master-report.txt`

### Estructura del reporte

```
=== auth-service ===
circleguarddevacr.azurecr.io/auth-service:v1.3.0 (debian 12.5)

┌─────────────────┬────────────────┬──────────┬───────────────────┬───────────────┬──────────────────────────────┐
│    Library      │ Vulnerability  │ Severity │ Installed Version │ Fixed Version │          Title               │
├─────────────────┼────────────────┼──────────┼───────────────────┼───────────────┼──────────────────────────────┤
│ libssl3         │ CVE-XXXX-XXXXX │   HIGH   │ 3.0.11-1          │ 3.0.13-1      │ OpenSSL: buffer overflow ... │
└─────────────────┴────────────────┴──────────┴───────────────────┴───────────────┴──────────────────────────────┘

[PASS] No CRITICAL vulnerabilities in auth-service

=== gateway-service ===
...
[PASS] No CRITICAL vulnerabilities in gateway-service
```

**Cómo leerlo:**
1. Ir al build en Jenkins → Artifacts → abrir `trivy-stage-report.txt`
2. Buscar `[FAIL]` para identificar servicios con CVEs críticos
3. La columna **Fixed Version** indica la versión del paquete que corrige la vulnerabilidad

---

## Supresión de falsos positivos

Si Trivy reporta un CVE como falso positivo (por ejemplo, un CVE que no aplica al contexto de uso del paquete), se puede suprimir añadiendo el ID en `.trivyignore` en la raíz del repositorio:

```
# .trivyignore
CVE-XXXX-XXXXX   # Razón: el código afectado no es ejecutado en este servicio
CVE-YYYY-YYYYY   # Razón: mitigado por configuración de red del cluster
```

El archivo `.trivyignore` se versiona junto al código para que las supresiones sean revisables en PRs.

---

## Cómo remediar un CVE

1. **Identificar el paquete** en el reporte (columna Library + Installed Version).
2. **Actualizar el Dockerfile** para usar una imagen base con la versión corregida:
   ```dockerfile
   # Antes (imagen base con el paquete vulnerable)
   FROM eclipse-temurin:21-jre-jammy
   # Después (actualizar a la versión con fix)
   FROM eclipse-temurin:21.0.3_9-jre-jammy
   ```
3. **Rebuild y re-scan**: el nuevo build del pipeline ejecutará Trivy nuevamente y verificará la corrección.
4. Si el fix no está disponible en la imagen base, actualizar el paquete explícitamente en el Dockerfile:
   ```dockerfile
   RUN apt-get update && apt-get install -y --only-upgrade libssl3
   ```

---

## Servicios escaneados

| Servicio | Imagen en ACR | Puerto |
|----------|--------------|--------|
| `auth-service` | `circleguarddevacr.azurecr.io/auth-service` | 8180 |
| `identity-service` | `circleguarddevacr.azurecr.io/identity-service` | 8083 |
| `form-service` | `circleguarddevacr.azurecr.io/form-service` | 8086 |
| `promotion-service` | `circleguarddevacr.azurecr.io/promotion-service` | 8088 |
| `notification-service` | `circleguarddevacr.azurecr.io/notification-service` | 8082 |
| `gateway-service` | `circleguarddevacr.azurecr.io/gateway-service` | 8087 |
| `dashboard-service` | `circleguarddevacr.azurecr.io/dashboard-service` | 8084 |
| `file-service` | `circleguarddevacr.azurecr.io/file-service` | 8085 |

---

## Relación con otros gates de seguridad

CircleGuard tiene tres capas de seguridad en el pipeline, complementarias entre sí:

| Gate | Herramienta | Qué detecta |
|------|-------------|-------------|
| Análisis de código fuente | SonarQube | Vulnerabilidades en el código Java, hotspots OWASP |
| Escaneo de imagen Docker | **Trivy** | CVEs en dependencias de OS y librerías de la imagen |
| Prueba de seguridad en runtime | OWASP ZAP | Vulnerabilidades HTTP en la aplicación desplegada |

---

## Archivos relevantes

| Archivo | Contenido |
|---------|-----------|
| `Jenkinsfile.stage` | Stage Trivy Scan con reporte por servicio |
| `Jenkinsfile.master` | Stage Trivy Scan + archivo `trivy-master-report.txt` |
| `.trivyignore` | CVEs excluidos del hard-fail |
