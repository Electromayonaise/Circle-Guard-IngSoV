module "aks" {
  source              = "./modules/aks-cluster"
  resource_group_name = var.resource_group_name
  location            = var.location
  cluster_name        = var.cluster_name
  node_count          = var.node_count
  vm_size             = var.vm_size
  environment         = var.environment
}

module "acr" {
  source              = "./modules/acr"
  acr_name            = var.acr_name
  resource_group_name = module.aks.resource_group_name
  location            = var.location
  aks_kubelet_id      = module.aks.kubelet_identity_id

  depends_on = [module.aks]
}

module "namespaces" {
  source     = "./modules/k8s-namespace"
  namespaces = var.namespaces

  depends_on = [module.aks]
}

module "infra" {
  source   = "./modules/k8s-infra"
  for_each = toset(var.namespaces)

  namespace         = each.value
  acr_login_server  = module.acr.login_server
  postgres_user     = var.postgres_user
  postgres_password = var.postgres_password
  neo4j_password    = var.neo4j_password
  jwt_secret        = var.jwt_secret

  depends_on = [module.namespaces, helm_release.cert_manager, helm_release.ingress_nginx]
}

# ── Shared cluster-wide infrastructure (primary workspace only) ───────────────

resource "helm_release" "cert_manager" {
  count            = var.deploy_shared_infra ? 1 : 0
  name             = "cert-manager"
  repository       = "https://charts.jetstack.io"
  chart            = "cert-manager"
  version          = "v1.14.5"
  namespace        = "cert-manager"
  create_namespace = true

  set {
    name  = "installCRDs"
    value = "true"
  }

  depends_on = [module.aks]
}

resource "helm_release" "ingress_nginx" {
  count            = var.deploy_shared_infra ? 1 : 0
  name             = "ingress-nginx"
  repository       = "https://kubernetes.github.io/ingress-nginx"
  chart            = "ingress-nginx"
  version          = "4.10.1"
  namespace        = "ingress-nginx"
  create_namespace = true

  # Allow scheduling on the Jenkins node when default nodes are resource-saturated
  set {
    name  = "controller.tolerations[0].key"
    value = "dedicated"
  }
  set {
    name  = "controller.tolerations[0].operator"
    value = "Equal"
  }
  set {
    name  = "controller.tolerations[0].value"
    value = "jenkins"
  }
  set {
    name  = "controller.tolerations[0].effect"
    value = "NoSchedule"
  }

  depends_on = [module.aks]
}

resource "kubernetes_manifest" "cluster_issuer_selfsigned" {
  count = var.deploy_shared_infra ? 1 : 0
  manifest = {
    apiVersion = "cert-manager.io/v1"
    kind       = "ClusterIssuer"
    metadata = {
      name = "selfsigned-issuer"
    }
    spec = {
      selfSigned = {}
    }
  }
  depends_on = [helm_release.cert_manager]
}

module "monitoring" {
  count  = var.deploy_shared_infra ? 1 : 0
  source = "./modules/monitoring"

  grafana_admin_password = var.grafana_admin_password

  depends_on = [module.aks]
}

module "jenkins" {
  count  = var.deploy_shared_infra ? 1 : 0
  source = "./modules/jenkins"

  acr_login_server  = module.acr.login_server
  jenkins_image_tag = var.jenkins_image_tag

  depends_on = [module.aks, module.acr]
}

