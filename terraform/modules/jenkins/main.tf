locals {
  ns = "jenkins"
}

resource "kubernetes_namespace" "jenkins" {
  metadata {
    name = local.ns
    labels = {
      project = "circleguard"
    }
  }
}

resource "kubernetes_persistent_volume_claim" "jenkins" {
  metadata {
    name      = "jenkins-pvc"
    namespace = local.ns
  }
  spec {
    access_modes = ["ReadWriteOnce"]
    resources {
      requests = { storage = "10Gi" }
    }
  }
  wait_until_bound = false
  depends_on       = [kubernetes_namespace.jenkins]
}

resource "kubernetes_deployment" "jenkins" {
  metadata {
    name      = "jenkins"
    namespace = local.ns
  }
  wait_for_rollout = false
  spec {
    replicas = 1
    strategy {
      type = "Recreate"
    }
    selector { match_labels = { app = "jenkins" } }
    template {
      metadata { labels = { app = "jenkins" } }
      spec {
        node_selector = { dedicated = "jenkins" }
        toleration {
          key      = "dedicated"
          operator = "Equal"
          value    = "jenkins"
          effect   = "NoSchedule"
        }
        security_context {
          fs_group = 1000
        }
        container {
          name  = "jenkins"
          image = "${var.acr_login_server}/jenkins:${var.jenkins_image_tag}"
          port {
            name           = "http"
            container_port = 8080
          }
          port {
            name           = "agent"
            container_port = 50000
          }
          env {
            name  = "DOCKER_HOST"
            value = "tcp://localhost:2375"
          }
          readiness_probe {
            http_get {
              path = "/login"
              port = "8080"
            }
            initial_delay_seconds = 60
            period_seconds        = 10
            failure_threshold     = 10
          }
          resources {
            requests = { memory = "1Gi", cpu = "500m" }
            limits   = { memory = "2Gi", cpu = "1500m" }
          }
          volume_mount {
            name       = "jenkins-home"
            mount_path = "/var/jenkins_home"
          }
        }
        container {
          name  = "dind"
          image = "docker:27-dind"
          security_context {
            privileged = true
          }
          env {
            name  = "DOCKER_TLS_CERTDIR"
            value = ""
          }
          resources {
            requests = { memory = "512Mi", cpu = "250m" }
            limits   = { memory = "3Gi", cpu = "1000m" }
          }
          volume_mount {
            name       = "docker-storage"
            mount_path = "/var/lib/docker"
          }
        }
        volume {
          name = "jenkins-home"
          persistent_volume_claim {
            claim_name = kubernetes_persistent_volume_claim.jenkins.metadata[0].name
          }
        }
        volume {
          name      = "docker-storage"
          empty_dir {}
        }
      }
    }
  }
}

resource "kubernetes_service" "jenkins" {
  metadata {
    name      = "jenkins"
    namespace = local.ns
  }
  spec {
    type     = "LoadBalancer"
    selector = { app = "jenkins" }
    port {
      name        = "http"
      port        = 8080
      target_port = 8080
    }
    port {
      name        = "agent"
      port        = 50000
      target_port = 50000
    }
  }
}
