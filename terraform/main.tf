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

  depends_on = [module.namespaces]
}

locals {
  services = [
    { name = "auth",         port = 8180, has_db = true,  db_name = "circleguard_auth" },
    { name = "identity",     port = 8083, has_db = true,  db_name = "circleguard_identity" },
    { name = "form",         port = 8086, has_db = true,  db_name = "circleguard_form" },
    { name = "promotion",    port = 8088, has_db = true,  db_name = "circleguard_promotion" },
    { name = "notification", port = 8082, has_db = false, db_name = "" },
    { name = "gateway",      port = 8087, has_db = false, db_name = "" },
    { name = "dashboard",    port = 8084, has_db = true,  db_name = "circleguard_dashboard" },
    { name = "file",         port = 8085, has_db = false, db_name = "" },
  ]
}

module "services" {
  source   = "./modules/k8s-service"
  for_each = { for pair in setproduct(var.namespaces, local.services) : "${pair[0]}-${pair[1].name}" => { namespace = pair[0], service = pair[1] } }

  namespace        = each.value.namespace
  service_name     = each.value.service.name
  service_port     = each.value.service.port
  acr_login_server = module.acr.login_server
  image_tag        = "latest"
  has_db           = each.value.service.has_db
  db_name          = each.value.service.db_name

  depends_on = [module.infra]
}
