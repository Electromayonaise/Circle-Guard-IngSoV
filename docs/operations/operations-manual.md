# Manual de Operaciones — CircleGuard

**Cluster:** Azure Kubernetes Service (AKS) — `circleguard-dev-aks`, resource group `circleguard-dev-rg`  
**Ambientes:** `dev` / `stage` / `master` (namespaces de Kubernetes)  
**Registry:** `circleguarddevacr.azurecr.io`

---

## 1. Accesos a Herramientas

### 1.1 Jenkins

- **URL:** `http://48.202.171.66:8080`
- **Credenciales:** usuario `admin`, contraseña en Kubernetes Secret `jenkins-secrets` namespace `jenkins`
- **Pipelines disponibles:**
  - `circleguard-dev` — branches `feature/*`
  - `circleguard-stage` — branch `develop`
  - `circleguard-master` — branch `main`

Para disparar un build manualmente (requiere crumb CSRF):

```powershell
$CRUMB = (curl.exe -s -c cookies.txt "http://48.202.171.66:8080/crumbIssuer/api/json" | ConvertFrom-Json).crumb
curl.exe -X POST -b cookies.txt -H "Jenkins-Crumb: $CRUMB" "http://48.202.171.66:8080/job/circleguard-stage/build"
```

### 1.2 Grafana

```powershell
kubectl port-forward svc/grafana -n monitoring 3000:3000
# Abrir: http://localhost:3000  (admin / admin, cambiar en primer acceso)
```

Dashboards disponibles:
- **CircleGuard Technical Dashboard** — HTTP rate, error rate, JVM heap, threads
- **CircleGuard Business Dashboard** — Log rate, error log count, recent error logs

### 1.3 Prometheus

```powershell
kubectl port-forward svc/prometheus -n monitoring 9090:9090
# Abrir: http://localhost:9090
```

Queries útiles:
```promql
# Request rate por servicio
rate(http_server_requests_seconds_count{namespace="dev"}[5m])

# Error rate 5xx
rate(http_server_requests_seconds_count{namespace="dev",status=~"5.."}[5m])

# JVM heap usado
jvm_memory_used_bytes{area="heap", namespace="dev"}
```

### 1.4 Loki / Grafana Explore

Acceder via Grafana → Explore → datasource Loki.

Queries útiles:
```logql
# Todos los logs del namespace dev
{namespace="dev"}

# Logs de un servicio específico
{namespace="dev", app="auth-service"}

# Filtrar errores
{namespace="dev"} |= "ERROR"

# Logs de un pod específico
{namespace="dev", pod=~"identity-service-.*"}
```

### 1.5 Zipkin

```powershell
kubectl port-forward svc/zipkin -n monitoring 9411:9411
# Abrir: http://localhost:9411
```

Para buscar trazas: seleccionar servicio, rango de tiempo y hacer "Find Traces". El campo "Lookback" acepta valores como `1h`, `6h`, `1d`.

---

## 2. Estado del Cluster

```powershell
# Ver todos los pods por namespace
kubectl get pods --all-namespaces

# Ver pods en namespace dev
kubectl get pods -n dev

# Ver estado de los deployments
kubectl get deployments -n dev

# Ver estado de los servicios (ports, IPs)
kubectl get svc -n dev

# Ver los nodos del cluster y su estado
kubectl get nodes -o wide

# Ver uso de recursos por nodo
kubectl top nodes

# Ver uso de recursos por pod
kubectl top pods -n dev
```

---

## 3. Logs

### 3.1 Logs en tiempo real

```powershell
# Logs de un servicio (últimas 100 líneas + follow)
kubectl logs -l app=auth-service -n dev --tail=100 -f

# Logs de un pod específico
kubectl logs <nombre-del-pod> -n dev -f

# Logs de un container específico (cuando hay sidecar)
kubectl logs <nombre-del-pod> -n dev -c <nombre-container> -f
```

### 3.2 Logs históricos vía Loki

Para logs de pods que ya no existen (terminados/reiniciados), usar Grafana Explore con Loki. Los logs persisten mientras el pod haya tenido Promtail activo y son indexados por `namespace`, `pod`, `container` y `app`.

### 3.3 Logs de Jenkins

```powershell
kubectl logs -l app=jenkins -n jenkins --tail=200 -f
```

---

## 4. Despliegue de Servicios

### 4.1 Despliegue automático (vía pipeline)

Todo despliegue debe pasar por el pipeline de Jenkins correspondiente:

| Acción | Cómo hacerlo |
|--------|-------------|
| Deploy a `dev` | Push a rama `feature/*` → Jenkins detecta via webhook |
| Deploy a `stage` | Push a `develop` → Jenkins detecta via webhook |
| Deploy a `master` | Merge PR `develop → main` con aprobación manual |

Los webhooks de GitHub apuntan a `http://48.202.171.66:8080/github-webhook/` y están configurados en el repositorio para disparar en los eventos `push` y `pull_request`.

### 4.2 Despliegue manual (emergencia)

Solo si el pipeline está caído y se requiere un fix urgente:

```powershell
# Actualizar imagen de un servicio
kubectl set image deployment/auth-service auth-service=circleguarddevacr.azurecr.io/auth-service:vX.Y.Z -n dev

# Verificar rollout
kubectl rollout status deployment/auth-service -n dev
```

---

## 5. Rollback

### 5.1 Rollback de Kubernetes (último deploy)

```powershell
# Rollback al ReplicaSet anterior
kubectl rollout undo deployment/auth-service -n dev

# Verificar estado post-rollback
kubectl rollout status deployment/auth-service -n dev
kubectl get pods -n dev -l app=auth-service
```

### 5.2 Rollback a versión específica

```powershell
# Ver historial de revisiones
kubectl rollout history deployment/auth-service -n dev

# Rollback a revisión específica
kubectl rollout undo deployment/auth-service -n dev --to-revision=3
```

### 5.3 Rollback de imagen Docker específica

```powershell
# Revertir a una versión taggeada anterior desde ACR
kubectl set image deployment/auth-service auth-service=circleguarddevacr.azurecr.io/auth-service:v1.2.3 -n dev
kubectl rollout status deployment/auth-service -n dev
```

### 5.4 Rollback de infraestructura Terraform

```powershell
# Ver versiones del estado en Terraform Cloud
terraform state list

# Si se necesita restaurar un estado anterior, hacerlo desde la UI de Terraform Cloud:
# https://app.terraform.io/app/IngSoV/workspaces/circleguard-dev/states
```

---

## 6. Reinicio de Servicios

```powershell
# Reiniciar un deployment (rolling restart)
kubectl rollout restart deployment/auth-service -n dev

# Reiniciar todos los deployments de un namespace
kubectl rollout restart deployment -n dev

# Forzar recreación de pods (útil si están en estado CrashLoopBackOff)
kubectl delete pods -l app=auth-service -n dev
```

---

## 7. Gestión de Secretos y Configuración

### 7.1 Ver secretos actuales

```powershell
# Listar secrets (no muestra valores)
kubectl get secrets -n dev

# Ver claves de un secret específico (sin valores)
kubectl describe secret circleguard-secrets -n dev
```

### 7.2 Actualizar un secret

```powershell
# Actualizar valor de una clave en el secret
kubectl patch secret circleguard-secrets -n dev --type='json' -p='[{"op":"replace","path":"/data/JWT_SECRET","value":"<base64-encoded-value>"}]'

# Codificar en base64 (PowerShell)
[Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes("nuevo-valor-secreto"))
```

Después de rotar un secret, reiniciar los deployments que lo consumen:

```powershell
kubectl rollout restart deployment -n dev
```

### 7.3 Ver ConfigMap

```powershell
kubectl get configmap circleguard-config -n dev -o yaml
```

### 7.4 Actualizar ConfigMap

Los cambios al ConfigMap via Terraform se aplican con `terraform apply`. Para cambios urgentes sin Terraform:

```powershell
kubectl edit configmap circleguard-config -n dev
# Luego reiniciar los pods para que tomen los nuevos valores
kubectl rollout restart deployment -n dev
```

---

## 8. Escalado

### 8.1 Escalado horizontal manual

```powershell
# Escalar un deployment a N réplicas
kubectl scale deployment/auth-service --replicas=3 -n dev

# Ver estado del escalado
kubectl get deployment auth-service -n dev
```

### 8.2 Escalado de nodos AKS

Escalar node pools se hace via Terraform:

```hcl
# En terraform/environments/dev.tfvars
node_count = 3  # cambiar el número de nodos
```

```powershell
terraform apply -var-file=environments/dev.tfvars
```

---

## 9. Infraestructura Terraform

### 9.1 Aplicar cambios de infraestructura

```powershell
cd terraform

# Preview de cambios
terraform plan -var-file=environments/dev.tfvars

# Aplicar cambios
terraform apply -var-file=environments/dev.tfvars
```

El estado se almacena en Terraform Cloud (workspace `circleguard-dev`, organización `IngSoV`). El pipeline de Jenkins aplica Terraform automáticamente si se detectan cambios en el directorio `terraform/`.

### 9.2 Ver estado actual de Terraform

```powershell
terraform state list
terraform show
```

### 9.3 Importar recurso existente a Terraform

```powershell
terraform import azurerm_resource_group.main /subscriptions/<SUB_ID>/resourceGroups/circleguard-dev-rg
```

---

## 10. CI/CD y Pipelines

### 10.1 Ver historial de builds

Acceder a Jenkins: `http://48.202.171.66:8080/job/circleguard-dev/`

### 10.2 Disparar build manualmente con webhook

```powershell
$CRUMB_JSON = curl.exe -s -u "admin:<password>" "http://48.202.171.66:8080/crumbIssuer/api/json"
$CRUMB = ($CRUMB_JSON | ConvertFrom-Json).crumb
curl.exe -X POST -u "admin:<password>" -H "Jenkins-Crumb: $CRUMB" "http://48.202.171.66:8080/job/circleguard-stage/build"
```

### 10.3 Ver artifacts de un build (Release Notes, ZAP report)

En Jenkins: `http://48.202.171.66:8080/job/circleguard-master/<build-number>/artifact/`

### 10.4 Troubleshooting de pipelines

Si un pipeline falla en el stage de Docker Build/Push:
```powershell
# Verificar que el Service Principal tiene acceso a ACR
kubectl get secret acr-secret -n jenkins
# El pod de Jenkins debe tener acceso al Docker socket (DinD)
kubectl get pods -n jenkins -o wide
kubectl describe pod <jenkins-pod> -n jenkins
```

---

## 11. Monitoreo y Alertas

### 11.1 Verificar targets de Prometheus

Acceder a `http://localhost:9090/targets` (con port-forward activo). Todos los microservicios deben aparecer en estado **UP**. Si alguno aparece **DOWN**:

1. Verificar que el pod esté Running: `kubectl get pods -n dev -l app=<servicio>`
2. Verificar que el actuator esté disponible: `kubectl exec -n dev <pod> -- curl localhost:<puerto>/actuator/prometheus`
3. Revisar la configuración de scrape en `k8s/observability/prometheus/configmap.yaml`

### 11.2 Verificar recolección de logs (Promtail)

```powershell
# Ver targets de Promtail
kubectl port-forward daemonset/promtail -n monitoring 9080:9080
# Abrir: http://localhost:9080/targets
# Todos los pods deben tener estado "active"
```

### 11.3 Verificar conectividad de Loki

En Grafana → Configuration → Data Sources → Loki → "Test". Debe mostrar "Data source connected and labels found".

---

## 12. Resolución de Problemas Comunes

### 12.1 Pod en CrashLoopBackOff

```powershell
# Ver últimas líneas de logs antes del crash
kubectl logs <pod> -n dev --previous --tail=50

# Ver eventos del pod
kubectl describe pod <pod> -n dev

# Causas comunes:
# - Secret o ConfigMap faltante
# - Puerto en conflicto
# - OOMKilled (aumentar memory limit en el deployment)
```

### 12.2 Pod en Pending (no scheduleado)

```powershell
kubectl describe pod <pod> -n dev
# Buscar en Events: "Insufficient memory", "Insufficient cpu", "no nodes available"

# Ver recursos disponibles
kubectl top nodes
kubectl describe nodes
```

### 12.3 Servicio no responde (503)

```powershell
# Verificar que el pod esté Ready
kubectl get pods -n dev -l app=<servicio>

# Verificar readiness probe
kubectl describe pod <pod> -n dev | Select-String -Pattern "Readiness"

# Hacer port-forward directo al pod (bypass de Service/Ingress)
kubectl port-forward pod/<pod> 9090:<puerto-servicio> -n dev
curl http://localhost:9090/actuator/health
```

### 12.4 Base de datos no conecta

```powershell
# Verificar que PostgreSQL esté Running
kubectl get pods -n dev -l app=circleguard-postgres

# Verificar conectividad desde otro pod
kubectl exec -it <pod-servicio> -n dev -- sh
# Dentro del pod:
# nc -zv circleguard-postgres 5432
```

### 12.5 Ingress / TLS no funciona

```powershell
# Ver estado del ingress
kubectl get ingress -n dev

# Ver logs del ingress controller
kubectl logs -l app.kubernetes.io/name=ingress-nginx -n ingress-nginx --tail=50

# Verificar certificado TLS
kubectl get certificate -n dev
kubectl describe certificate gateway-tls -n dev
```

---

## 13. Acceso al Cluster AKS

### 13.1 Configurar kubectl

```powershell
az login
az aks get-credentials --resource-group circleguard-dev-rg --name circleguard-dev-aks
kubectl config current-context
```

### 13.2 Cambiar entre contextos

```powershell
kubectl config get-contexts
kubectl config use-context circleguard-dev-aks
```

### 13.3 Acceso con namespace por defecto

```powershell
kubectl config set-context --current --namespace=dev
# Ahora todos los comandos kubectl operan en el namespace dev por defecto
```
