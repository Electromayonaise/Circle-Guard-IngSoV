# Change Management y Release Notes — CircleGuard

## 1. Proceso Formal de Change Management

### 1.1 Tipos de Cambio

| Tipo | Prefijo de commit | Impacto en versión | Ejemplo |
|------|------------------|--------------------|---------|
| Breaking Change | `BREAKING CHANGE` en cuerpo | MAJOR (`v2.0.0`) | Cambio de contrato de API |
| Nueva funcionalidad | `feat:` | MINOR (`v1.3.0`) | Nuevo endpoint |
| Corrección de bug | `fix:` | PATCH (`v1.2.1`) | Fix en validación |
| Documentación | `docs:` | PATCH | Actualizar README |
| CI/CD / infra | `ci:`, `chore:` | PATCH | Ajuste de pipeline |

Todos los commits siguen la especificación **Conventional Commits** para permitir la generación automática de Release Notes y el versionado semántico.

---

### 1.2 Flujo de Cambios por Ambiente

```
feature/xxx  ──PR──▶  develop  ──PR──▶  main
                         │                │
                    Pipeline Dev      Pipeline Master
                    Pipeline Stage    (producción)
                    (ambos sobre develop)
```

Los pipelines de **Dev** y **Stage** comparten el mismo branch `develop` como fuente de verdad. Cada push a `develop` dispara ambos pipelines en paralelo sobre el mismo commit, desplegando en namespaces de Kubernetes independientes (`dev` y `stage`). Esta decisión de diseño se justifica a continuación.

#### Justificación: Dev y Stage sobre el mismo branch `develop`

Mantener un branch `stage` separado introduciría una brecha temporal entre lo que dev valida y lo que stage despliega, lo que va en contra del principio de integración continua. Al correr ambos pipelines sobre el mismo commit de `develop`:

- **Validación en paralelo:** Dev ejecuta CI rápido (build + unit tests + smoke), Stage ejecuta validación más profunda (integración + E2E + ZAP) — ambos sobre el mismo código, sin desfase.
- **Feedback más temprano:** Los fallos de integración o E2E se detectan en el mismo ciclo que el merge a `develop`, no en un merge posterior a un branch de stage.
- **Menor complejidad de branching:** El equipo gestiona un solo branch de integración (`develop`) en lugar de mantener sincronizados `develop` → `stage` → `main`.
- **Namespace como frontera de ambiente:** El aislamiento entre dev y stage lo provee Kubernetes a través de namespaces separados, no el control de versiones. Esto es suficiente para los objetivos del proyecto.

El branch `main` sigue siendo la única frontera real de promoción a producción, protegida por PR review y el pipeline de master con gates más estrictos.

#### Ambiente Dev (namespace `dev`, branch `develop`)
- **Trigger:** Push a `develop` vía GitHub webhook.
- **Criterio de entrada:** PR aprobado por al menos 1 revisor.
- **Qué ejecuta:** Build, análisis estático, pruebas unitarias, Docker build/push, deploy al namespace `dev`, smoke tests.
- **Criterio de salida:** Todos los stages en verde.

#### Ambiente Stage (namespace `stage`, branch `develop`)
- **Trigger:** Push a `develop` vía GitHub webhook (mismo evento que dev).
- **Criterio de entrada:** Mismo PR que dispara dev.
- **Qué ejecuta:** Build + pruebas unitarias, Docker build/push, deploy al namespace `stage`, pruebas de integración, pruebas E2E, OWASP ZAP scan.
- **Criterio de salida:** Pruebas E2E verdes, reporte ZAP sin alertas críticas.

#### Ambiente Master/Producción (namespace `master`, branch `main`)
- **Trigger:** PR de `develop` a `main`, aprobado y mergeado.
- **Criterio de entrada:** Pipelines de dev y stage verdes sobre el último commit de `develop`, PR revisado.
- **Qué ejecuta:** Build + pruebas unitarias + verificación de cobertura JaCoCo (≥60% LINE), Docker build/push con tag semántico, deploy al namespace `master`, pruebas de sistema, OWASP ZAP scan, pruebas de rendimiento (Locust), generación de Release Notes, git tag de versión.
- **Criterio de salida:** Todos los stages verdes, tag publicado en GitHub, Release Notes archivadas en Jenkins.

---

### 1.3 Criterios de Aprobación de PR

| Criterio | Dev + Stage | Master |
|----------|-------------|--------|
| Pipeline de rama verde | ✅ | ✅ |
| Al menos 1 aprobación | ✅ | ✅ |
| Cobertura JaCoCo ≥ 60% | ✅ | ✅ |
| Pruebas E2E verdes | Stage ✅ | ✅ |
| Revisión de reporte ZAP | Stage ✅ | ✅ |
| Revisión de rendimiento (Locust) | — | ✅ |

---

## 2. Sistema de Versionado Semántico y Etiquetado de Releases

### 2.1 Cálculo Automático de Versión

El pipeline de master calcula la versión automáticamente en el stage **Compute Version**, siguiendo [SemVer 2.0](https://semver.org/):

```
VERSION = vMAJOR.MINOR.PATCH
```

**Reglas de incremento** (evaluadas sobre commits desde el último tag):

```groovy
if (BREAKING CHANGE en mensajes) → MAJOR++, MINOR=0, PATCH=0
else if (feat: en mensajes)      → MINOR++, PATCH=0
else                             → PATCH++
```

Si no existe ningún tag previo, la versión base es `v0.0.0`.

### 2.2 Etiquetado en GitHub

Al finalizar exitosamente el pipeline de master, Jenkins crea y publica el tag:

```bash
git tag -a v1.3.0 -m "Release v1.3.0 -- Build #42"
git push origin v1.3.0
```

Las imágenes Docker se publican en ACR con tres tags simultáneos:
- `circleguarddevacr.azurecr.io/circleguard-{svc}:v1.3.0` — versión inmutable
- `circleguarddevacr.azurecr.io/circleguard-{svc}:latest` — última versión estable

### 2.3 Historial de Versiones

Los tags son visibles en GitHub en la sección **Releases** del repositorio y en ACR. Cada tag corresponde a un build de Jenkins que puede ser trazado por número de build (`Build #N`).

---

## 3. Generación Automática de Release Notes

### 3.1 Mecanismo

El pipeline de master genera `RELEASE_NOTES.md` automáticamente en el stage **Generate Release Notes**, analizando los commits desde el último tag hasta `HEAD`:

```bash
git log <último-tag>..HEAD --pretty=format:'%s' --no-merges
```

Los commits se clasifican por prefijo convencional:

| Categoría | Prefijo detectado |
|-----------|------------------|
| Breaking Changes | `BREAKING CHANGE` |
| New Features | `feat:` / `feat(scope):` |
| Bug Fixes | `fix:` / `fix(scope):` |
| Other Changes | cualquier otro commit |

### 3.2 Formato Generado

```markdown
# Release Notes -- v1.3.0

**Release Date:** 2026-05-15
**Environment:** master
**Build:** #42

## Breaking Changes
_None_

## New Features
- feat(security): RBAC y TLS con cert-manager para gateway-service

## Bug Fixes
- fix(ci): wait for ingress-nginx admission webhook before applying Ingress

## Other Changes
- docs(change-mgmt): proceso formal de change management y planes de rollback
```

### 3.3 Acceso a las Release Notes

- **Jenkins:** Artifacts del build de master → `RELEASE_NOTES.md`
- **GitHub:** Historial de commits por tag en la pestaña Releases

---

## 4. Planes de Rollback

### 4.1 Rollback en Dev (namespace `dev`)

**Escenario:** Deploy roto en dev, necesidad de restaurar servicio rápidamente.

**Opción A — Rollback de deployment (sin re-deploy):**
```bash
kubectl rollout undo deployment/{svc}-service -n dev
# Ejemplo:
kubectl rollout undo deployment/gateway-service -n dev
kubectl rollout status deployment/gateway-service -n dev --timeout=300s
```

**Opción B — Re-deploy de imagen anterior:**
```bash
kubectl set image deployment/{svc}-service \
    {svc}-service=circleguarddevacr.azurecr.io/circleguard-{svc}:dev-{BUILD_ANTERIOR} \
    -n dev
```

**Verificación:**
```bash
kubectl get pods -n dev -l app={svc}-service
kubectl logs -l app={svc}-service -n dev --tail=50
```

---

### 4.2 Rollback en Stage (namespace `stage`)

**Escenario:** Pruebas de integración o E2E fallaron después del deploy.

**Opción A — Rollback de deployment:**
```bash
kubectl rollout undo deployment/{svc}-service -n stage
kubectl rollout status deployment/{svc}-service -n stage --timeout=300s
```

**Opción B — Re-deploy de imagen con tag anterior:**
```bash
kubectl set image deployment/{svc}-service \
    {svc}-service=circleguarddevacr.azurecr.io/circleguard-{svc}:stage-{BUILD_ANTERIOR} \
    -n stage
```

**Rollback completo de ambiente** (todos los servicios):
```bash
PREV_BUILD=24  # número del build anterior estable
for svc in auth identity form promotion notification gateway dashboard file; do
    kubectl set image deployment/${svc}-service \
        ${svc}-service=circleguarddevacr.azurecr.io/circleguard-${svc}:stage-${PREV_BUILD} \
        -n stage
done
kubectl rollout status deployment/gateway-service -n stage --timeout=300s
```

---

### 4.3 Rollback en Master/Producción (namespace `master`)

El rollback en producción es el más crítico e implica tres pasos coordinados: revertir el deploy, revertir el tag de git, y notificar al equipo.

#### Paso 1 — Rollback inmediato del deployment

```bash
# Rollback de un servicio específico
kubectl rollout undo deployment/{svc}-service -n master
kubectl rollout status deployment/{svc}-service -n master --timeout=600s

# Rollback de todos los servicios simultáneamente
for svc in auth identity form promotion notification gateway dashboard file; do
    kubectl rollout undo deployment/${svc}-service -n master &
done
wait
```

#### Paso 2 — Verificar versión activa

```bash
kubectl get deployment -n master -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.template.spec.containers[0].image}{"\n"}{end}'
```

#### Paso 3 — Re-deploy explícito de versión anterior (recomendado sobre rollout undo)

```bash
PREV_VERSION=v1.2.3  # última versión estable
for svc in auth identity form promotion notification gateway dashboard file; do
    kubectl set image deployment/${svc}-service \
        ${svc}-service=circleguarddevacr.azurecr.io/circleguard-${svc}:${PREV_VERSION} \
        -n master
done
```

#### Paso 4 — Revertir tag de git (si el tag fue publicado)

```bash
# Eliminar tag remoto defectuoso
git push origin :refs/tags/v1.3.0

# Eliminar tag local
git tag -d v1.3.0
```

#### Paso 5 — Verificación de salud post-rollback

```bash
# Confirmar pods en Running
kubectl get pods -n master

# Verificar readiness de servicios críticos
kubectl get endpoints -n master

# Revisar logs de los últimos 5 minutos
kubectl logs -l app=gateway-service -n master --since=5m
```

---

### 4.4 Matriz de Decisión de Rollback

| Situación | Acción recomendada | Tiempo estimado |
|-----------|-------------------|-----------------|
| Pod crasheando en dev | `kubectl rollout undo` | < 2 min |
| E2E fallando en stage | `kubectl rollout undo` todos los servicios | < 5 min |
| Incidente en master — 1 servicio | `kubectl rollout undo` + verificación | < 5 min |
| Incidente en master — múltiples servicios | Re-deploy de versión anterior (`PREV_VERSION`) | < 10 min |
| Corrupción de datos / DB migration | Restaurar backup + re-deploy versión anterior | Depende del backup |

---

## 5. Trazabilidad

Cada cambio en producción es completamente trazable:

```
Commit (Conventional) 
    → PR aprobado 
        → Build Jenkins #N 
            → Image tag (vMAJOR.MINOR.PATCH) en ACR 
                → Git tag en GitHub 
                    → RELEASE_NOTES.md archivado en Jenkins
```

Para trazar un incidente en producción:
1. Identificar la imagen activa: `kubectl describe deployment/{svc}-service -n master | grep Image`
2. Extraer el tag de versión de la imagen
3. Buscar el tag en GitHub → ver los commits incluidos
4. Identificar el PR y el build de Jenkins asociado
