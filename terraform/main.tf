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

