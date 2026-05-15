resource "kubernetes_deployment" "service" {
  wait_for_rollout = false

  metadata {
    name      = "${var.service_name}-service"
    namespace = var.namespace
  }
  spec {
    replicas = var.replicas
    selector { match_labels = { app = "${var.service_name}-service" } }
    template {
      metadata { labels = { app = "${var.service_name}-service" } }
      spec {
        container {
          name  = "${var.service_name}-service"
          image = "${var.acr_login_server}/circleguard-${var.service_name}:${var.image_tag}"
          port { container_port = var.service_port }
          env_from {
            config_map_ref { name = "circleguard-config" }
          }
          env_from {
            secret_ref { name = "circleguard-secrets" }
          }
          dynamic "env" {
            for_each = var.has_db ? [1] : []
            content {
              name  = "SPRING_DATASOURCE_URL"
              value = "jdbc:postgresql://$(POSTGRES_HOST):$(POSTGRES_PORT)/${var.db_name}"
            }
          }
          dynamic "env" {
            for_each = var.has_db ? [1] : []
            content {
              name  = "SPRING_DATASOURCE_USERNAME"
              value_from {
                secret_key_ref {
                  name = "circleguard-secrets"
                  key  = "POSTGRES_USER"
                }
              }
            }
          }
          dynamic "env" {
            for_each = var.has_db ? [1] : []
            content {
              name  = "SPRING_DATASOURCE_PASSWORD"
              value_from {
                secret_key_ref {
                  name = "circleguard-secrets"
                  key  = "POSTGRES_PASSWORD"
                }
              }
            }
          }
          dynamic "env" {
            for_each = var.has_db ? [1] : []
            content {
              name  = "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE"
              value = "5"
            }
          }
          readiness_probe {
            tcp_socket { port = var.service_port }
            initial_delay_seconds = 30
            period_seconds        = 10
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "service" {
  metadata {
    name      = "${var.service_name}-service"
    namespace = var.namespace
  }
  spec {
    selector = { app = "${var.service_name}-service" }
    port {
      port        = var.service_port
      target_port = var.service_port
    }
  }
}
