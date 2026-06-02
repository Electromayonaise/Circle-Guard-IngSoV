locals {
  ns = "monitoring"
}

resource "kubernetes_namespace" "monitoring" {
  metadata {
    name = local.ns
    labels = {
      project = "circleguard"
    }
  }
}

# ── Prometheus ────────────────────────────────────────────────────────────────

resource "kubernetes_config_map" "prometheus" {
  metadata {
    name      = "prometheus-config"
    namespace = local.ns
  }
  data = {
    "prometheus.yml" = <<-EOF
      global:
        scrape_interval: 15s
        evaluation_interval: 15s

      scrape_configs:
        - job_name: circleguard-dev
          metrics_path: /actuator/prometheus
          static_configs:
            - targets:
                - auth-service.dev.svc.cluster.local:8180
                - identity-service.dev.svc.cluster.local:8083
                - form-service.dev.svc.cluster.local:8086
                - promotion-service.dev.svc.cluster.local:8088
                - notification-service.dev.svc.cluster.local:8082
                - gateway-service.dev.svc.cluster.local:8087
                - dashboard-service.dev.svc.cluster.local:8084
                - file-service.dev.svc.cluster.local:8085
              labels:
                namespace: dev
                environment: dev

        - job_name: circleguard-stage
          metrics_path: /actuator/prometheus
          static_configs:
            - targets:
                - auth-service.stage.svc.cluster.local:8180
                - identity-service.stage.svc.cluster.local:8083
                - form-service.stage.svc.cluster.local:8086
                - promotion-service.stage.svc.cluster.local:8088
                - notification-service.stage.svc.cluster.local:8082
                - gateway-service.stage.svc.cluster.local:8087
                - dashboard-service.stage.svc.cluster.local:8084
                - file-service.stage.svc.cluster.local:8085
              labels:
                namespace: stage
                environment: stage

        - job_name: circleguard-master
          metrics_path: /actuator/prometheus
          static_configs:
            - targets:
                - auth-service.master.svc.cluster.local:8180
                - identity-service.master.svc.cluster.local:8083
                - form-service.master.svc.cluster.local:8086
                - promotion-service.master.svc.cluster.local:8088
                - notification-service.master.svc.cluster.local:8082
                - gateway-service.master.svc.cluster.local:8087
                - dashboard-service.master.svc.cluster.local:8084
                - file-service.master.svc.cluster.local:8085
              labels:
                namespace: master
                environment: master

        - job_name: prometheus
          static_configs:
            - targets:
                - localhost:9090
    EOF
  }
  lifecycle {
    ignore_changes = [data]
  }
  depends_on = [kubernetes_namespace.monitoring]
}

resource "kubernetes_deployment" "prometheus" {
  metadata {
    name      = "prometheus"
    namespace = local.ns
  }
  wait_for_rollout = false
  spec {
    replicas = 1
    selector { match_labels = { app = "prometheus" } }
    template {
      metadata { labels = { app = "prometheus" } }
      spec {
        container {
          name  = "prometheus"
          image = "prom/prometheus:v2.51.2"
          args = [
            "--config.file=/etc/prometheus/prometheus.yml",
            "--storage.tsdb.retention.time=7d",
            "--web.enable-lifecycle",
          ]
          port { container_port = 9090 }
          readiness_probe {
            http_get {
              path = "/-/ready"
              port = "9090"
            }
            initial_delay_seconds = 10
            period_seconds        = 10
          }
          resources {
            requests = { memory = "256Mi", cpu = "100m" }
            limits   = { memory = "512Mi", cpu = "500m" }
          }
          volume_mount {
            name       = "config"
            mount_path = "/etc/prometheus"
          }
          volume_mount {
            name       = "storage"
            mount_path = "/prometheus"
          }
        }
        volume {
          name = "config"
          config_map { name = kubernetes_config_map.prometheus.metadata[0].name }
        }
        volume {
          name      = "storage"
          empty_dir {}
        }
      }
    }
  }
}

resource "kubernetes_service" "prometheus" {
  metadata {
    name      = "prometheus"
    namespace = local.ns
  }
  spec {
    selector = { app = "prometheus" }
    port {
      port        = 9090
      target_port = 9090
    }
  }
}

# ── Loki ─────────────────────────────────────────────────────────────────────

resource "kubernetes_config_map" "loki" {
  metadata {
    name      = "loki-config"
    namespace = local.ns
  }
  data = {
    "loki.yaml" = <<-EOF
      auth_enabled: false

      server:
        http_listen_port: 3100

      common:
        instance_addr: 127.0.0.1
        path_prefix: /loki
        storage:
          filesystem:
            chunks_directory: /loki/chunks
            rules_directory: /loki/rules
        replication_factor: 1
        ring:
          kvstore:
            store: inmemory

      schema_config:
        configs:
          - from: 2020-10-24
            store: tsdb
            object_store: filesystem
            schema: v12
            index:
              prefix: index_
              period: 24h

      analytics:
        reporting_enabled: false
    EOF
  }
  lifecycle {
    ignore_changes = [data]
  }
  depends_on = [kubernetes_namespace.monitoring]
}

resource "kubernetes_deployment" "loki" {
  metadata {
    name      = "loki"
    namespace = local.ns
  }
  wait_for_rollout = false
  spec {
    replicas = 1
    selector { match_labels = { app = "loki" } }
    template {
      metadata { labels = { app = "loki" } }
      spec {
        container {
          name  = "loki"
          image = "grafana/loki:2.9.4"
          args  = ["-config.file=/etc/loki/loki.yaml"]
          port { container_port = 3100 }
          readiness_probe {
            http_get {
              path = "/ready"
              port = "3100"
            }
            initial_delay_seconds = 15
            period_seconds        = 10
          }
          resources {
            requests = { memory = "128Mi", cpu = "50m" }
            limits   = { memory = "256Mi", cpu = "200m" }
          }
          volume_mount {
            name       = "config"
            mount_path = "/etc/loki"
          }
          volume_mount {
            name       = "storage"
            mount_path = "/loki"
          }
        }
        volume {
          name = "config"
          config_map { name = kubernetes_config_map.loki.metadata[0].name }
        }
        volume {
          name      = "storage"
          empty_dir {}
        }
      }
    }
  }
}

resource "kubernetes_service" "loki" {
  metadata {
    name      = "loki"
    namespace = local.ns
  }
  spec {
    selector = { app = "loki" }
    port {
      port        = 3100
      target_port = 3100
    }
  }
}

# ── Zipkin ────────────────────────────────────────────────────────────────────

resource "kubernetes_deployment" "zipkin" {
  metadata {
    name      = "zipkin"
    namespace = local.ns
  }
  wait_for_rollout = false
  spec {
    replicas = 1
    selector { match_labels = { app = "zipkin" } }
    template {
      metadata { labels = { app = "zipkin" } }
      spec {
        container {
          name  = "zipkin"
          image = "openzipkin/zipkin:3"
          port { container_port = 9411 }
          readiness_probe {
            http_get {
              path = "/health"
              port = "9411"
            }
            initial_delay_seconds = 10
            period_seconds        = 10
          }
          resources {
            requests = { memory = "256Mi", cpu = "50m" }
            limits   = { memory = "512Mi", cpu = "300m" }
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "zipkin" {
  metadata {
    name      = "zipkin"
    namespace = local.ns
  }
  spec {
    selector = { app = "zipkin" }
    port {
      port        = 9411
      target_port = 9411
    }
  }
}

# ── Grafana ───────────────────────────────────────────────────────────────────

resource "kubernetes_config_map" "grafana_datasources" {
  metadata {
    name      = "grafana-datasources"
    namespace = local.ns
  }
  data = {
    "datasources.yaml" = <<-EOF
      apiVersion: 1
      datasources:
        - name: Prometheus
          type: prometheus
          uid: prometheus
          url: http://prometheus.monitoring.svc.cluster.local:9090
          isDefault: true
          jsonData:
            httpMethod: POST
        - name: Loki
          type: loki
          uid: loki
          url: http://loki.monitoring.svc.cluster.local:3100
    EOF
  }
  lifecycle {
    ignore_changes = [data]
  }
  depends_on = [kubernetes_namespace.monitoring]
}

resource "kubernetes_config_map" "grafana_dashboard_provider" {
  metadata {
    name      = "grafana-dashboard-provider"
    namespace = local.ns
  }
  data = {
    "dashboards.yaml" = <<-EOF
      apiVersion: 1
      providers:
        - name: CircleGuard
          orgId: 1
          folder: CircleGuard
          type: file
          disableDeletion: false
          editable: true
          options:
            path: /var/lib/grafana/dashboards
    EOF
  }
  lifecycle {
    ignore_changes = [data]
  }
  depends_on = [kubernetes_namespace.monitoring]
}

resource "kubernetes_config_map" "grafana_dashboard_technical" {
  metadata {
    name      = "grafana-dashboard-technical"
    namespace = local.ns
  }
  data = {
    "technical-dashboard.json" = jsonencode({
      annotations  = { list = [] }
      editable     = true
      graphTooltip = 0
      id           = null
      links        = []
      panels = [
        {
          datasource  = { type = "prometheus", uid = "prometheus" }
          fieldConfig = { defaults = { color = { mode = "palette-classic" }, custom = { lineWidth = 1 }, unit = "reqps" }, overrides = [] }
          gridPos     = { h = 8, w = 12, x = 0, y = 0 }
          id          = 1
          options     = { legend = { displayMode = "list", placement = "bottom", showLegend = true }, tooltip = { mode = "single" } }
          targets = [{ datasource = { type = "prometheus", uid = "prometheus" }, expr = "sum(rate(http_server_requests_seconds_count{job=~\"circleguard-.*\"}[5m])) by (job)", legendFormat = "{{job}}", refId = "A" }]
          title = "HTTP Request Rate"
          type  = "timeseries"
        },
        {
          datasource  = { type = "prometheus", uid = "prometheus" }
          fieldConfig = { defaults = { color = { mode = "palette-classic" }, custom = { lineWidth = 1 }, unit = "reqps" }, overrides = [] }
          gridPos     = { h = 8, w = 12, x = 12, y = 0 }
          id          = 2
          options     = { legend = { displayMode = "list", placement = "bottom", showLegend = true }, tooltip = { mode = "single" } }
          targets = [{ datasource = { type = "prometheus", uid = "prometheus" }, expr = "sum(rate(http_server_requests_seconds_count{job=~\"circleguard-.*\",status=~\"4..|5..\"}[5m])) by (job)", legendFormat = "{{job}}", refId = "A" }]
          title = "HTTP Error Rate (4xx/5xx)"
          type  = "timeseries"
        },
        {
          datasource  = { type = "prometheus", uid = "prometheus" }
          fieldConfig = { defaults = { color = { mode = "palette-classic" }, custom = { lineWidth = 1 }, unit = "bytes" }, overrides = [] }
          gridPos     = { h = 8, w = 12, x = 0, y = 8 }
          id          = 3
          options     = { legend = { displayMode = "list", placement = "bottom", showLegend = true }, tooltip = { mode = "single" } }
          targets = [{ datasource = { type = "prometheus", uid = "prometheus" }, expr = "sum(jvm_memory_used_bytes{job=~\"circleguard-.*\",area=\"heap\"}) by (job)", legendFormat = "{{job}}", refId = "A" }]
          title = "JVM Heap Used"
          type  = "timeseries"
        },
        {
          datasource  = { type = "prometheus", uid = "prometheus" }
          fieldConfig = { defaults = { color = { mode = "palette-classic" }, custom = { lineWidth = 1 }, unit = "short" }, overrides = [] }
          gridPos     = { h = 8, w = 12, x = 12, y = 8 }
          id          = 4
          options     = { legend = { displayMode = "list", placement = "bottom", showLegend = true }, tooltip = { mode = "single" } }
          targets = [{ datasource = { type = "prometheus", uid = "prometheus" }, expr = "jvm_threads_live_threads{job=~\"circleguard-.*\"}", legendFormat = "{{job}}", refId = "A" }]
          title = "JVM Live Threads"
          type  = "timeseries"
        }
      ]
      refresh       = "30s"
      schemaVersion = 38
      tags          = ["circleguard", "technical"]
      time          = { from = "now-1h", to = "now" }
      timepicker    = {}
      timezone      = "browser"
      title         = "CircleGuard - Technical Dashboard"
      uid           = "circleguard-technical"
      version       = 1
    })
  }
  lifecycle {
    ignore_changes = [data]
  }
  depends_on = [kubernetes_namespace.monitoring]
}

resource "kubernetes_config_map" "grafana_dashboard_business" {
  metadata {
    name      = "grafana-dashboard-business"
    namespace = local.ns
  }
  data = {
    "business-dashboard.json" = "{}"
  }
  lifecycle {
    ignore_changes = [data]
  }
  depends_on = [kubernetes_namespace.monitoring]
}

resource "kubernetes_config_map" "grafana_alerting" {
  metadata {
    name      = "grafana-alerting"
    namespace = local.ns
  }
  data = {
    "alerting.yaml" = "apiVersion: 1\ngroups: []"
  }
  lifecycle {
    ignore_changes = [data]
  }
  depends_on = [kubernetes_namespace.monitoring]
}

resource "kubernetes_deployment" "grafana" {
  metadata {
    name      = "grafana"
    namespace = local.ns
  }
  wait_for_rollout = false
  spec {
    replicas = 1
    selector { match_labels = { app = "grafana" } }
    template {
      metadata { labels = { app = "grafana" } }
      spec {
        container {
          name  = "grafana"
          image = "grafana/grafana:10.4.1"
          port { container_port = 3000 }
          env {
            name  = "GF_SECURITY_ADMIN_USER"
            value = "admin"
          }
          env {
            name  = "GF_SECURITY_ADMIN_PASSWORD"
            value = var.grafana_admin_password
          }
          env {
            name  = "GF_AUTH_ANONYMOUS_ENABLED"
            value = "false"
          }
          readiness_probe {
            http_get {
              path = "/api/health"
              port = "3000"
            }
            initial_delay_seconds = 15
            period_seconds        = 10
          }
          resources {
            requests = { memory = "128Mi", cpu = "50m" }
            limits   = { memory = "256Mi", cpu = "200m" }
          }
          volume_mount {
            name       = "datasources"
            mount_path = "/etc/grafana/provisioning/datasources"
          }
          volume_mount {
            name       = "dashboard-provider"
            mount_path = "/etc/grafana/provisioning/dashboards"
          }
          volume_mount {
            name       = "dashboard-technical"
            mount_path = "/var/lib/grafana/dashboards/technical-dashboard.json"
            sub_path   = "technical-dashboard.json"
          }
          volume_mount {
            name       = "dashboard-business"
            mount_path = "/var/lib/grafana/dashboards/business-dashboard.json"
            sub_path   = "business-dashboard.json"
          }
          volume_mount {
            name       = "alerting"
            mount_path = "/etc/grafana/provisioning/alerting"
          }
          volume_mount {
            name       = "storage"
            mount_path = "/var/lib/grafana"
          }
        }
        volume {
          name = "datasources"
          config_map { name = kubernetes_config_map.grafana_datasources.metadata[0].name }
        }
        volume {
          name = "dashboard-provider"
          config_map { name = kubernetes_config_map.grafana_dashboard_provider.metadata[0].name }
        }
        volume {
          name = "dashboard-technical"
          config_map { name = kubernetes_config_map.grafana_dashboard_technical.metadata[0].name }
        }
        volume {
          name = "dashboard-business"
          config_map { name = kubernetes_config_map.grafana_dashboard_business.metadata[0].name }
        }
        volume {
          name = "alerting"
          config_map { name = kubernetes_config_map.grafana_alerting.metadata[0].name }
        }
        volume {
          name      = "storage"
          empty_dir {}
        }
      }
    }
  }
}

resource "kubernetes_service" "grafana" {
  metadata {
    name      = "grafana"
    namespace = local.ns
  }
  spec {
    selector = { app = "grafana" }
    port {
      port        = 3000
      target_port = 3000
    }
  }
}

# ── Promtail ──────────────────────────────────────────────────────────────────

resource "kubernetes_service_account_v1" "promtail" {
  metadata {
    name      = "promtail"
    namespace = local.ns
  }
  depends_on = [kubernetes_namespace.monitoring]
}

resource "kubernetes_cluster_role_v1" "promtail" {
  metadata {
    name = "promtail"
  }
  rule {
    api_groups = [""]
    resources  = ["nodes", "pods", "namespaces"]
    verbs      = ["get", "list", "watch"]
  }
}

resource "kubernetes_cluster_role_binding_v1" "promtail" {
  metadata {
    name = "promtail"
  }
  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "ClusterRole"
    name      = kubernetes_cluster_role_v1.promtail.metadata[0].name
  }
  subject {
    kind      = "ServiceAccount"
    name      = kubernetes_service_account_v1.promtail.metadata[0].name
    namespace = local.ns
  }
}

resource "kubernetes_config_map" "promtail" {
  metadata {
    name      = "promtail-config"
    namespace = local.ns
  }
  data = {
    "promtail.yaml" = <<-EOF
      server:
        http_listen_port: 9080
        grpc_listen_port: 0

      positions:
        filename: /tmp/positions.yaml

      clients:
        - url: http://loki.monitoring.svc.cluster.local:3100/loki/api/v1/push

      scrape_configs:
        - job_name: kubernetes-pods
          kubernetes_sd_configs:
            - role: pod
          pipeline_stages:
            - cri: {}
          relabel_configs:
            - source_labels: [__meta_kubernetes_pod_node_name]
              target_label: __host__
            - source_labels: [__meta_kubernetes_namespace]
              action: replace
              target_label: namespace
            - source_labels: [__meta_kubernetes_pod_name]
              action: replace
              target_label: pod
            - source_labels: [__meta_kubernetes_pod_container_name]
              action: replace
              target_label: container
            - source_labels: [__meta_kubernetes_pod_label_app]
              action: replace
              target_label: app
            - source_labels: [__meta_kubernetes_namespace, __meta_kubernetes_pod_name, __meta_kubernetes_pod_uid, __meta_kubernetes_pod_container_name]
              separator: /
              regex: (.+)/(.+)/(.+)/(.+)
              target_label: __path__
              replacement: /var/log/pods/${1}_${2}_${3}/${4}/*.log
    EOF
  }
  lifecycle {
    ignore_changes = [data]
  }
  depends_on = [kubernetes_namespace.monitoring]
}

resource "kubernetes_daemon_set_v1" "promtail" {
  metadata {
    name      = "promtail"
    namespace = local.ns
  }
  spec {
    selector { match_labels = { app = "promtail" } }
    template {
      metadata { labels = { app = "promtail" } }
      spec {
        service_account_name = kubernetes_service_account_v1.promtail.metadata[0].name
        toleration {
          key      = "node-role.kubernetes.io/master"
          operator = "Exists"
          effect   = "NoSchedule"
        }
        container {
          name  = "promtail"
          image = "grafana/promtail:2.9.4"
          args  = ["-config.file=/etc/promtail/promtail.yaml"]
          port { container_port = 9080 }
          env {
            name = "HOSTNAME"
            value_from {
              field_ref { field_path = "spec.nodeName" }
            }
          }
          resources {
            requests = { memory = "64Mi", cpu = "20m" }
            limits   = { memory = "128Mi", cpu = "100m" }
          }
          volume_mount {
            name       = "config"
            mount_path = "/etc/promtail"
          }
          volume_mount {
            name       = "varlogpods"
            mount_path = "/var/log/pods"
            read_only  = true
          }
        }
        volume {
          name = "config"
          config_map { name = kubernetes_config_map.promtail.metadata[0].name }
        }
        volume {
          name = "varlogpods"
          host_path { path = "/var/log/pods" }
        }
      }
    }
  }
}
