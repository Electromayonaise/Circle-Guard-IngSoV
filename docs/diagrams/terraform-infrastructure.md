# Terraform Infrastructure Architecture

> **Rendering:** This document uses [Mermaid](https://mermaid.js.org/) diagrams that render automatically on GitHub.
> To export as PNG/SVG (e.g., for presentations), paste any diagram block into [mermaid.live](https://mermaid.live) and use the export button.

---

## Overview

Terraform manages all Azure cloud resources and Kubernetes infrastructure for CircleGuard. It does **not** manage application service deployments — those are owned exclusively by Jenkins pipelines.

**Remote state backend:** HCP Terraform Cloud, org `IngSoV`, workspace `circleguard-dev`.

The infrastructure is organized into four reusable modules called from a single root module (`terraform/main.tf`):

| Module | Provisions |
|---|---|
| `aks-cluster` | Azure Resource Group + AKS cluster with 2 nodes |
| `acr` | Azure Container Registry + AcrPull role for AKS |
| `k8s-namespace` | Kubernetes namespaces (`dev`, `stage`, `master`) |
| `k8s-infra` | Infra stack per namespace (PostgreSQL, Kafka, Redis, Neo4j, Secrets, ConfigMap) |

---

## Module Dependency Graph

The four modules have a strict dependency chain: AKS must exist before anything else, ACR and namespaces are provisioned in parallel once AKS is ready, and the infra stack is deployed last into each namespace.

```mermaid
graph TD
    ROOT["<b>Root Module</b><br/>terraform/main.tf"]

    ROOT -->|"cluster config"| AKS["<b>aks-cluster</b><br/>Azure Resource Group<br/>AKS Cluster"]
    AKS -->|"depends_on"| ACR["<b>acr</b><br/>Container Registry<br/>AcrPull role binding"]
    AKS -->|"depends_on"| NS["<b>k8s-namespace</b><br/>Namespaces:<br/>dev / stage / master"]
    NS -->|"for_each namespace<br/>depends_on"| INFRA["<b>k8s-infra</b><br/>PostgreSQL + Kafka<br/>Redis + Neo4j<br/>Secrets + ConfigMap"]
```

---

## Azure Resources

Terraform provisions two Azure resources inside a dedicated resource group. The AKS cluster uses a system-assigned managed identity; its kubelet identity receives `AcrPull` permissions on the registry so pods can pull images without credentials.

```mermaid
graph LR
    subgraph AZ["Azure — eastus"]
        RG["Resource Group<br/><i>circleguard-dev-rg</i>"]

        subgraph AKS["AKS Cluster — circleguard-dev-aks"]
            NP["Default Node Pool<br/>2 nodes · Standard_B2s<br/>Kubernetes 1.34 (pinned)"]
            OID["OIDC Issuer<br/>Workload Identity enabled"]
            ID["SystemAssigned Identity<br/>kubelet_identity_id"]
        end

        subgraph ACR_BOX["Container Registry"]
            ACR_NAME["circleguarddevacr.azurecr.io<br/>SKU: Basic"]
            ROLE["Role Assignment<br/>AcrPull → kubelet identity"]
        end

        RG --> AKS
        RG --> ACR_BOX
        ID -->|"grants"| ROLE
    end
```

> **Why Kubernetes 1.34 is pinned:** Without an explicit version, Azure automatically upgrades the cluster during `terraform apply`, causing a 40-minute operation. Pinning prevents unintended upgrades.

---

## Kubernetes Resources (per namespace)

The `k8s-infra` module is instantiated three times — once for `dev`, `stage`, and `master`. Each namespace gets an identical, isolated infra stack. All four databases share the same `circleguard-secrets` Secret and `circleguard-config` ConfigMap.

```mermaid
graph TD
    subgraph NS["Namespace: dev  (identical stack in stage and master)"]
        direction TB

        subgraph SECRETS["Credentials"]
            S["Secret: circleguard-secrets<br/>POSTGRES_USER · POSTGRES_PASSWORD<br/>NEO4J_AUTH · NEO4J_PASSWORD · JWT_SECRET"]
            C["ConfigMap: circleguard-config<br/>POSTGRES_HOST:5432 · REDIS_HOST:6379<br/>KAFKA_BOOTSTRAP:9092 · NEO4J_URI:7687<br/>JWT_EXPIRATION: 86400000"]
        end

        subgraph PG["PostgreSQL 16"]
            PVC["PersistentVolumeClaim<br/>2Gi RWO (Azure Disk)"]
            PG_INIT["ConfigMap: postgres-init<br/>creates 6 databases on first boot"]
            PG_DEP["Deployment: circleguard-postgres<br/>credentials from Secret<br/>PGDATA = /data/pgdata (subdir)"]
            PG_SVC["Service: circleguard-postgres:5432"]
        end

        subgraph KF["Apache Kafka 3.7.0 (KRaft, no ZooKeeper)"]
            KF_CFG["ConfigMap: kafka-server-config<br/>KRaft mode · single broker/controller"]
            KF_DEP["Deployment: circleguard-kafka<br/>init container: kafka-storage format"]
            KF_SVC["Service: circleguard-kafka<br/>:9092 (broker) · :9093 (controller)"]
        end

        subgraph RD["Redis 7 Alpine"]
            RD_DEP["Deployment: circleguard-redis"]
            RD_SVC["Service: circleguard-redis:6379"]
        end

        subgraph N4J["Neo4j 5 + APOC plugin"]
            N4J_DEP["Deployment: circleguard-neo4j<br/>NEO4J_AUTH from Secret"]
            N4J_SVC["Service: circleguard-neo4j<br/>:7474 (HTTP) · :7687 (Bolt)"]
        end

        S -->|"env"| PG_DEP
        S -->|"NEO4J_AUTH"| N4J_DEP
        PG_INIT --> PG_DEP
        PVC --> PG_DEP
        KF_CFG --> KF_DEP
        PG_DEP --> PG_SVC
        KF_DEP --> KF_SVC
        RD_DEP --> RD_SVC
        N4J_DEP --> N4J_SVC
    end
```

---

## Responsibility Boundary

```mermaid
graph LR
    TF["Terraform<br/>(this document)"]
    JK["Jenkins Pipelines<br/>Jenkinsfile.dev/stage/master"]

    TF -->|"provisions"| AZURE["Azure resources<br/>Resource Group · AKS · ACR"]
    TF -->|"provisions"| K8S_INFRA["Kubernetes infra<br/>Namespaces · Secrets · ConfigMaps<br/>PostgreSQL · Kafka · Redis · Neo4j"]

    JK -->|"builds & pushes"| IMAGES["Docker images → ACR"]
    JK -->|"deploys"| SERVICES["Application services → AKS<br/>auth · identity · form · promotion<br/>notification · gateway · dashboard · file"]
```

> Keeping these concerns separate means infrastructure changes (e.g., scaling nodes, rotating secrets) never require a Jenkins build, and application deployments never risk modifying infrastructure state.

---

## Terraform Variables

All sensitive values are stored as encrypted variables in HCP Terraform Cloud and never committed to the repository.

| Variable | Description | Sensitive |
|---|---|:---:|
| `resource_group_name` | Azure resource group name | |
| `cluster_name` | AKS cluster name | |
| `acr_name` | ACR registry name | |
| `environment` | Environment label (tag) | |
| `postgres_user` | PostgreSQL username | |
| `postgres_password` | PostgreSQL password | Yes |
| `neo4j_password` | Neo4j password | Yes |
| `jwt_secret` | JWT signing secret | Yes |
| `ARM_CLIENT_ID` | Azure Service Principal ID | |
| `ARM_CLIENT_SECRET` | Azure Service Principal secret | Yes |
| `ARM_TENANT_ID` | Azure tenant ID | |
| `ARM_SUBSCRIPTION_ID` | Azure subscription ID | |
