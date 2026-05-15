locals {
  ns = var.namespace
}

# ── Secrets ─────────────────────────────────────────────────────────────────

resource "kubernetes_secret" "circleguard" {
  metadata {
    name      = "circleguard-secrets"
    namespace = local.ns
  }
  data = {
    POSTGRES_USER     = var.postgres_user
    POSTGRES_PASSWORD = var.postgres_password
    NEO4J_AUTH        = "neo4j/${var.neo4j_password}"
    NEO4J_PASSWORD    = var.neo4j_password
    JWT_SECRET        = var.jwt_secret
  }
}

# ── PostgreSQL ──────────────────────────────────────────────────────────────

resource "kubernetes_persistent_volume_claim" "postgres" {
  metadata {
    name      = "postgres-pvc"
    namespace = local.ns
  }
  spec {
    access_modes = ["ReadWriteOnce"]
    resources {
      requests = { storage = "2Gi" }
    }
  }
  wait_until_bound = false
}

resource "kubernetes_config_map" "postgres_init" {
  metadata {
    name      = "postgres-init"
    namespace = local.ns
  }
  data = {
    "init.sh" = <<-EOF
      #!/bin/bash
      set -e
      psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
        CREATE DATABASE circleguard_identity;
        CREATE DATABASE circleguard_form;
        CREATE DATABASE circleguard_promotion;
        CREATE DATABASE circleguard_dashboard;
        CREATE DATABASE circleguard_file;
      EOSQL
    EOF
  }
}

resource "kubernetes_deployment" "postgres" {
  metadata {
    name      = "circleguard-postgres"
    namespace = local.ns
  }
  wait_for_rollout = false
  spec {
    replicas = 1
    strategy {
      type = "Recreate"
    }
    selector { match_labels = { app = "circleguard-postgres" } }
    template {
      metadata { labels = { app = "circleguard-postgres" } }
      spec {
        termination_grace_period_seconds = 60
        container {
          name  = "postgres"
          image = "postgres:16"
          env {
            name = "POSTGRES_USER"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.circleguard.metadata[0].name
                key  = "POSTGRES_USER"
              }
            }
          }
          env {
            name = "POSTGRES_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.circleguard.metadata[0].name
                key  = "POSTGRES_PASSWORD"
              }
            }
          }
          env {
            name  = "POSTGRES_DB"
            value = "circleguard_auth"
          }
          env {
            name  = "PGDATA"
            value = "/var/lib/postgresql/data/pgdata"
          }
          port { container_port = 5432 }
          lifecycle {
            pre_stop {
              exec {
                command = ["su", "-", "postgres", "-c", "pg_ctl stop -m fast -D /var/lib/postgresql/data/pgdata"]
              }
            }
          }
          readiness_probe {
            tcp_socket { port = "5432" }
            initial_delay_seconds = 10
            period_seconds        = 5
            timeout_seconds       = 3
          }
          resources {
            requests = {
              memory = "256Mi"
              cpu    = "100m"
            }
            limits = {
              memory = "512Mi"
              cpu    = "500m"
            }
          }
          volume_mount {
            name       = "postgres-data"
            mount_path = "/var/lib/postgresql/data"
          }
          volume_mount {
            name       = "init-scripts"
            mount_path = "/docker-entrypoint-initdb.d"
          }
        }
        volume {
          name = "postgres-data"
          persistent_volume_claim { claim_name = kubernetes_persistent_volume_claim.postgres.metadata[0].name }
        }
        volume {
          name = "init-scripts"
          config_map {
            name         = kubernetes_config_map.postgres_init.metadata[0].name
            default_mode = "0755"
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "postgres" {
  metadata {
    name      = "circleguard-postgres"
    namespace = local.ns
  }
  spec {
    selector = { app = "circleguard-postgres" }
    port {
      port        = 5432
      target_port = 5432
    }
  }
}

# ── Kafka ────────────────────────────────────────────────────────────────────

resource "kubernetes_config_map" "kafka" {
  metadata {
    name      = "kafka-server-config"
    namespace = local.ns
  }
  data = {
    "server.properties" = <<-EOF
      node.id=1
      process.roles=broker,controller
      listeners=PLAINTEXT://:9092,CONTROLLER://:9093
      advertised.listeners=PLAINTEXT://circleguard-kafka:9092
      controller.listener.names=CONTROLLER
      listener.security.protocol.map=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      controller.quorum.voters=1@localhost:9093
      log.dirs=/kafka-data
      offsets.topic.replication.factor=1
      transaction.state.log.replication.factor=1
      transaction.state.log.min.isr=1
      auto.create.topics.enable=true
      num.partitions=1
    EOF
  }
}

resource "kubernetes_deployment" "kafka" {
  metadata {
    name      = "circleguard-kafka"
    namespace = local.ns
  }
  wait_for_rollout = false
  spec {
    replicas = 1
    selector { match_labels = { app = "circleguard-kafka" } }
    template {
      metadata { labels = { app = "circleguard-kafka" } }
      spec {
        init_container {
          name  = "kafka-init"
          image = "apache/kafka:3.7.0"
          command = ["/bin/sh", "-c", <<-EOF
            cp /kafka-config/server.properties /tmp/server.properties
            /opt/kafka/bin/kafka-storage.sh format \
              -t MkU3OEVBNTcwNTJENDM2Qk \
              -c /tmp/server.properties \
              --ignore-formatted || true
          EOF
          ]
          volume_mount {
            name       = "kafka-config"
            mount_path = "/kafka-config"
          }
          volume_mount {
            name       = "kafka-data"
            mount_path = "/kafka-data"
          }
        }
        container {
          name  = "kafka"
          image = "apache/kafka:3.7.0"
          command = ["/bin/sh", "-c", <<-EOF
            cp /kafka-config/server.properties /tmp/server.properties
            exec /opt/kafka/bin/kafka-server-start.sh /tmp/server.properties
          EOF
          ]
          port { container_port = 9092 }
          port { container_port = 9093 }
          readiness_probe {
            tcp_socket { port = "9092" }
            initial_delay_seconds = 20
            period_seconds        = 10
            timeout_seconds       = 5
          }
          volume_mount {
            name       = "kafka-config"
            mount_path = "/kafka-config"
          }
          volume_mount {
            name       = "kafka-data"
            mount_path = "/kafka-data"
          }
        }
        volume {
          name = "kafka-config"
          config_map { name = kubernetes_config_map.kafka.metadata[0].name }
        }
        volume {
          name      = "kafka-data"
          empty_dir {}
        }
      }
    }
  }
}

resource "kubernetes_service" "kafka" {
  metadata {
    name      = "circleguard-kafka"
    namespace = local.ns
  }
  spec {
    selector = { app = "circleguard-kafka" }
    port {
      name        = "broker"
      port        = 9092
      target_port = 9092
    }
    port {
      name        = "controller"
      port        = 9093
      target_port = 9093
    }
  }
}

# ── Redis ────────────────────────────────────────────────────────────────────

resource "kubernetes_deployment" "redis" {
  metadata {
    name      = "circleguard-redis"
    namespace = local.ns
  }
  wait_for_rollout = false
  spec {
    replicas = 1
    selector { match_labels = { app = "circleguard-redis" } }
    template {
      metadata { labels = { app = "circleguard-redis" } }
      spec {
        container {
          name  = "redis"
          image = "redis:7.2"
          port { container_port = 6379 }
          readiness_probe {
            exec { command = ["redis-cli", "ping"] }
            initial_delay_seconds = 5
            period_seconds        = 5
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "redis" {
  metadata {
    name      = "circleguard-redis"
    namespace = local.ns
  }
  spec {
    selector = { app = "circleguard-redis" }
    port {
      port        = 6379
      target_port = 6379
    }
  }
}

# ── Neo4j ────────────────────────────────────────────────────────────────────

resource "kubernetes_persistent_volume_claim" "neo4j" {
  metadata {
    name      = "neo4j-pvc"
    namespace = local.ns
  }
  spec {
    access_modes = ["ReadWriteOnce"]
    resources {
      requests = { storage = "1Gi" }
    }
  }
  wait_until_bound = false
}

resource "kubernetes_deployment" "neo4j" {
  metadata {
    name      = "circleguard-neo4j"
    namespace = local.ns
  }
  wait_for_rollout = false
  spec {
    replicas = 1
    selector { match_labels = { app = "circleguard-neo4j" } }
    template {
      metadata { labels = { app = "circleguard-neo4j" } }
      spec {
        container {
          name  = "neo4j"
          image = "neo4j:5"
          env {
            name = "NEO4J_AUTH"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.circleguard.metadata[0].name
                key  = "NEO4J_AUTH"
              }
            }
          }
          env {
            name  = "NEO4J_PLUGINS"
            value = "[\"apoc\"]"
          }
          port { container_port = 7474 }
          port { container_port = 7687 }
          readiness_probe {
            tcp_socket { port = "7687" }
            initial_delay_seconds = 30
            period_seconds        = 10
          }
          volume_mount {
            name       = "neo4j-data"
            mount_path = "/data"
          }
        }
        volume {
          name = "neo4j-data"
          persistent_volume_claim { claim_name = kubernetes_persistent_volume_claim.neo4j.metadata[0].name }
        }
      }
    }
  }
}

resource "kubernetes_service" "neo4j" {
  metadata {
    name      = "circleguard-neo4j"
    namespace = local.ns
  }
  spec {
    selector = { app = "circleguard-neo4j" }
    port {
      name        = "http"
      port        = 7474
      target_port = 7474
    }
    port {
      name        = "bolt"
      port        = 7687
      target_port = 7687
    }
  }
}

# ── ConfigMap ────────────────────────────────────────────────────────────────

resource "kubernetes_config_map" "circleguard_config" {
  metadata {
    name      = "circleguard-config"
    namespace = local.ns
  }
  data = {
    POSTGRES_HOST  = "circleguard-postgres"
    POSTGRES_PORT  = "5432"
    REDIS_HOST     = "circleguard-redis"
    REDIS_PORT     = "6379"
    KAFKA_BOOTSTRAP = "circleguard-kafka:9092"
    NEO4J_URI      = "bolt://circleguard-neo4j:7687"
    JWT_EXPIRATION = "86400000"
  }

  depends_on = [
    kubernetes_deployment.postgres,
    kubernetes_deployment.kafka,
    kubernetes_deployment.redis,
    kubernetes_deployment.neo4j,
  ]
}
