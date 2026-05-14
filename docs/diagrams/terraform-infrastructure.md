# Arquitectura de Infraestructura Terraform

> **Visualización:** Este documento utiliza diagramas [Mermaid](https://mermaid.js.org/) que se renderizan automáticamente en GitHub.
> Para exportar como PNG/SVG (presentaciones, documentos), pega cualquier bloque de diagrama en [mermaid.live](https://mermaid.live) y usa el botón de exportar.

---

## Descripción General

Terraform gestiona todos los recursos de Azure y la infraestructura de Kubernetes para CircleGuard. **No gestiona los despliegues de servicios de aplicación** — esos son responsabilidad exclusiva de los pipelines de Jenkins.

**Backend remoto:** HCP Terraform Cloud, organización `IngSoV`, workspace `circleguard-dev`.

La infraestructura se organiza en cuatro módulos reutilizables invocados desde el módulo raíz (`terraform/main.tf`):

| Módulo | Aprovisiona |
|---|---|
| `aks-cluster` | Resource Group de Azure + clúster AKS con 2 nodos |
| `acr` | Azure Container Registry + rol AcrPull para AKS |
| `k8s-namespace` | Namespaces de Kubernetes (`dev`, `stage`, `master`) |
| `k8s-infra` | Stack de infra por namespace (PostgreSQL, Kafka, Redis, Neo4j, Secrets, ConfigMap) |

---

## Grafo de Dependencias entre Módulos

Los módulos tienen una cadena de dependencias estricta: AKS debe existir antes que cualquier otro recurso. Una vez listo AKS, el registry y los namespaces se aprovisionan en paralelo. Finalmente, el stack de infra se despliega en cada namespace.

```mermaid
graph TD
    ROOT["<b>Módulo Raíz</b><br/>terraform/main.tf"]

    ROOT -->|"config del clúster"| AKS["<b>aks-cluster</b><br/>Resource Group de Azure<br/>Clúster AKS"]
    AKS -->|"depends_on"| ACR["<b>acr</b><br/>Container Registry<br/>Binding de rol AcrPull"]
    AKS -->|"depends_on"| NS["<b>k8s-namespace</b><br/>Namespaces:<br/>dev / stage / master"]
    NS -->|"for_each namespace<br/>depends_on"| INFRA["<b>k8s-infra</b><br/>PostgreSQL + Kafka<br/>Redis + Neo4j<br/>Secrets + ConfigMap"]
```

---

## Recursos de Azure

Terraform aprovisiona dos recursos de Azure dentro de un Resource Group dedicado. El clúster AKS usa una identidad administrada asignada por el sistema; su identidad de kubelet recibe permisos `AcrPull` sobre el registry para que los pods puedan descargar imágenes sin credenciales adicionales.

```mermaid
graph LR
    subgraph AZ["Azure — eastus"]
        RG["Resource Group<br/><i>circleguard-dev-rg</i>"]

        subgraph AKS_BOX["Clúster AKS — circleguard-dev-aks"]
            NP["Node Pool por defecto<br/>2 nodos · Standard_B4ms<br/>Kubernetes 1.34 (fijado)"]
            OID["OIDC Issuer habilitado<br/>Workload Identity habilitado"]
            ID["Identidad SystemAssigned<br/>kubelet_identity_id"]
        end

        subgraph ACR_BOX["Container Registry"]
            ACR_NAME["circleguarddevacr.azurecr.io<br/>SKU: Basic"]
            ROLE["Role Assignment<br/>AcrPull → identidad kubelet"]
        end

        RG --> AKS_BOX
        RG --> ACR_BOX
        ID -->|"otorga"| ROLE
    end
```

> **Por qué Kubernetes 1.34 está fijado:** Sin una versión explícita, Azure actualiza el clúster automáticamente durante `terraform apply`, generando una operación de 40 minutos. Fijar la versión evita actualizaciones no planificadas.

---

## Recursos de Kubernetes (por namespace)

El módulo `k8s-infra` se instancia tres veces — una por cada namespace (`dev`, `stage`, `master`). Cada namespace recibe un stack de infra idéntico e independiente.

Los cuatro componentes de infra exponen sus endpoints a través del ConfigMap `circleguard-config`, que los servicios de aplicación consumen para conectarse. Las credenciales sensibles se almacenan en el Secret `circleguard-secrets`.

```mermaid
graph TD
    subgraph NS["Namespace: dev  (stack idéntico en stage y master)"]
        direction TB

        subgraph CREDS["Configuración y Credenciales"]
            S["Secret: circleguard-secrets<br/>POSTGRES_USER · POSTGRES_PASSWORD<br/>NEO4J_AUTH · NEO4J_PASSWORD · JWT_SECRET"]
            C["ConfigMap: circleguard-config<br/>POSTGRES_HOST:5432 · REDIS_HOST:6379<br/>KAFKA_BOOTSTRAP:9092 · NEO4J_URI:7687<br/>JWT_EXPIRATION: 86400000"]
        end

        subgraph PG["PostgreSQL 16"]
            PVC["PersistentVolumeClaim<br/>2Gi RWO (Azure Disk)"]
            PG_INIT["ConfigMap: postgres-init<br/>crea 6 bases de datos al arrancar"]
            PG_DEP["Deployment: circleguard-postgres<br/>credenciales desde Secret<br/>PGDATA = /data/pgdata"]
            PG_SVC["Service: circleguard-postgres:5432"]
        end

        subgraph KF["Apache Kafka 3.7.0 (KRaft, sin ZooKeeper)"]
            KF_CFG["ConfigMap: kafka-server-config<br/>modo KRaft · broker y controller en uno"]
            KF_DEP["Deployment: circleguard-kafka<br/>init container: kafka-storage format"]
            KF_SVC["Service: circleguard-kafka<br/>:9092 (broker) · :9093 (controller)"]
        end

        subgraph RD["Redis 7 Alpine"]
            RD_DEP["Deployment: circleguard-redis<br/>sin credenciales requeridas"]
            RD_SVC["Service: circleguard-redis:6379"]
        end

        subgraph N4J["Neo4j 5 + plugin APOC"]
            N4J_DEP["Deployment: circleguard-neo4j<br/>NEO4J_AUTH desde Secret"]
            N4J_SVC["Service: circleguard-neo4j<br/>:7474 (HTTP) · :7687 (Bolt)"]
        end

        S -->|"POSTGRES_USER/PASSWORD"| PG_DEP
        S -->|"NEO4J_AUTH"| N4J_DEP
        PG_INIT -->|"script de init"| PG_DEP
        PVC -->|"volumen de datos"| PG_DEP
        KF_CFG -->|"configuración"| KF_DEP

        PG_DEP --> PG_SVC
        KF_DEP --> KF_SVC
        RD_DEP --> RD_SVC
        N4J_DEP --> N4J_SVC

        PG_SVC -->|"endpoint"| C
        KF_SVC -->|"endpoint"| C
        RD_SVC -->|"endpoint"| C
        N4J_SVC -->|"endpoint"| C
    end
```

---

## Límite de Responsabilidades

```mermaid
graph LR
    TF["Terraform"]
    JK["Pipelines Jenkins<br/>Jenkinsfile.dev/stage/master"]

    TF -->|"aprovisiona"| AZURE["Recursos Azure<br/>Resource Group · AKS · ACR"]
    TF -->|"aprovisiona"| K8S_INFRA["Infra Kubernetes<br/>Namespaces · Secrets · ConfigMaps<br/>PostgreSQL · Kafka · Redis · Neo4j"]

    JK -->|"construye y sube"| IMAGES["Imágenes Docker → ACR"]
    JK -->|"despliega"| SERVICES["Servicios de aplicación → AKS<br/>auth · identity · form · promotion<br/>notification · gateway · dashboard · file"]
```

> Separar estas responsabilidades garantiza que los cambios de infraestructura (escalar nodos, rotar secrets) no requieren un build de Jenkins, y que los despliegues de aplicación nunca modifican el estado de la infraestructura.

---

## Variables de Entrada (HCP Terraform Cloud)

Todos los valores sensibles se almacenan como variables cifradas en HCP Terraform Cloud y nunca se commitean al repositorio.

| Variable | Descripción | Sensible |
|---|---|:---:|
| `resource_group_name` | Nombre del Resource Group de Azure | |
| `cluster_name` | Nombre del clúster AKS | |
| `acr_name` | Nombre del registry ACR | |
| `environment` | Etiqueta de entorno | |
| `postgres_user` | Usuario de PostgreSQL | |
| `postgres_password` | Contraseña de PostgreSQL | Sí |
| `neo4j_password` | Contraseña de Neo4j | Sí |
| `jwt_secret` | Secreto para firma de JWT | Sí |
| `ARM_CLIENT_ID` | ID del Service Principal de Azure | |
| `ARM_CLIENT_SECRET` | Secreto del Service Principal | Sí |
| `ARM_TENANT_ID` | ID del tenant de Azure | |
| `ARM_SUBSCRIPTION_ID` | ID de la suscripción de Azure | |
