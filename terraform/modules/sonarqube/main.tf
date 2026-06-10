locals {
  ns = "sonarqube"
}

resource "kubernetes_namespace" "sonarqube" {
  metadata {
    name   = local.ns
    labels = { project = "circleguard" }
  }
}

resource "kubernetes_secret" "postgres" {
  metadata {
    name      = "sonarqube-postgres-secret"
    namespace = local.ns
  }
  data = {
    POSTGRES_DB       = "sonar"
    POSTGRES_USER     = "sonar"
    POSTGRES_PASSWORD = var.db_password
  }
  depends_on = [kubernetes_namespace.sonarqube]
}

resource "kubernetes_persistent_volume_claim" "postgres" {
  metadata {
    name      = "sonarqube-postgres-pvc"
    namespace = local.ns
  }
  spec {
    access_modes = ["ReadWriteOnce"]
    resources { requests = { storage = "5Gi" } }
  }
  wait_until_bound = false
  depends_on       = [kubernetes_namespace.sonarqube]
}

resource "kubernetes_persistent_volume_claim" "sonarqube" {
  metadata {
    name      = "sonarqube-data-pvc"
    namespace = local.ns
  }
  spec {
    access_modes = ["ReadWriteOnce"]
    resources { requests = { storage = "5Gi" } }
  }
  wait_until_bound = false
  depends_on       = [kubernetes_namespace.sonarqube]
}

resource "kubernetes_deployment" "postgres" {
  metadata {
    name      = "sonarqube-postgres"
    namespace = local.ns
  }
  wait_for_rollout = false
  spec {
    replicas = 1
    strategy { type = "Recreate" }
    selector { match_labels = { app = "sonarqube-postgres" } }
    template {
      metadata { labels = { app = "sonarqube-postgres" } }
      spec {
        container {
          name  = "postgres"
          image = "postgres:15-alpine"
          env_from {
            secret_ref { name = kubernetes_secret.postgres.metadata[0].name }
          }
          port { container_port = 5432 }
          resources {
            requests = { memory = "256Mi", cpu = "100m" }
            limits   = { memory = "512Mi", cpu = "500m" }
          }
          volume_mount {
            name       = "data"
            mount_path = "/var/lib/postgresql/data"
          }
        }
        volume {
          name = "data"
          persistent_volume_claim {
            claim_name = kubernetes_persistent_volume_claim.postgres.metadata[0].name
          }
        }
      }
    }
  }
  depends_on = [kubernetes_secret.postgres, kubernetes_persistent_volume_claim.postgres]
}

resource "kubernetes_service" "postgres" {
  metadata {
    name      = "sonarqube-postgres"
    namespace = local.ns
  }
  spec {
    selector = { app = "sonarqube-postgres" }
    port {
      port        = 5432
      target_port = 5432
    }
  }
}

resource "kubernetes_deployment" "sonarqube" {
  metadata {
    name      = "sonarqube"
    namespace = local.ns
  }
  wait_for_rollout = false
  spec {
    replicas = 1
    strategy { type = "Recreate" }
    selector { match_labels = { app = "sonarqube" } }
    template {
      metadata { labels = { app = "sonarqube" } }
      spec {
        init_container {
          name  = "init-sysctl"
          image = "busybox:1.36"
          security_context { privileged = true }
          command = ["sh", "-c", "sysctl -w vm.max_map_count=524288 && sysctl -w fs.file-max=131072"]
        }
        container {
          name  = "sonarqube"
          image = "sonarqube:10.4-community"
          env {
            name  = "SONAR_JDBC_URL"
            value = "jdbc:postgresql://sonarqube-postgres.${local.ns}.svc.cluster.local:5432/sonar"
          }
          env {
            name = "SONAR_JDBC_USERNAME"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.postgres.metadata[0].name
                key  = "POSTGRES_USER"
              }
            }
          }
          env {
            name = "SONAR_JDBC_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.postgres.metadata[0].name
                key  = "POSTGRES_PASSWORD"
              }
            }
          }
          env { name = "SONAR_WEB_JAVAOPTS";    value = "-Xmx512m -Xms256m" }
          env { name = "SONAR_CE_JAVAOPTS";     value = "-Xmx512m -Xms256m" }
          env { name = "SONAR_SEARCH_JAVAOPTS"; value = "-Xmx512m -Xms512m -Xss1m" }
          port { container_port = 9000 }
          readiness_probe {
            http_get { path = "/api/system/status"; port = "9000" }
            initial_delay_seconds = 60
            period_seconds        = 10
            failure_threshold     = 20
          }
          resources {
            requests = { memory = "1Gi",  cpu = "500m"  }
            limits   = { memory = "2Gi",  cpu = "1000m" }
          }
          volume_mount {
            name       = "data"
            mount_path = "/opt/sonarqube/data"
          }
        }
        volume {
          name = "data"
          persistent_volume_claim {
            claim_name = kubernetes_persistent_volume_claim.sonarqube.metadata[0].name
          }
        }
      }
    }
  }
  depends_on = [kubernetes_deployment.postgres, kubernetes_persistent_volume_claim.sonarqube]
}

resource "kubernetes_service" "sonarqube" {
  metadata {
    name      = "sonarqube"
    namespace = local.ns
  }
  spec {
    type     = "ClusterIP"
    selector = { app = "sonarqube" }
    port {
      port        = 9000
      target_port = 9000
    }
  }
}

resource "kubernetes_ingress_v1" "sonarqube" {
  metadata {
    name      = "sonarqube-ingress"
    namespace = local.ns
    annotations = {
      "nginx.ingress.kubernetes.io/proxy-body-size"    = "64m"
      "nginx.ingress.kubernetes.io/proxy-read-timeout" = "600"
      "nginx.ingress.kubernetes.io/proxy-send-timeout" = "600"
    }
  }
  spec {
    ingress_class_name = "nginx"
    rule {
      http {
        path {
          path      = "/"
          path_type = "Prefix"
          backend {
            service {
              name = kubernetes_service.sonarqube.metadata[0].name
              port { number = 9000 }
            }
          }
        }
      }
    }
  }
  depends_on = [kubernetes_deployment.sonarqube]
}
