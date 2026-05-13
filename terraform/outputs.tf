output "acr_login_server" {
  description = "ACR login server URL — use this in Jenkinsfile as REGISTRY"
  value       = module.acr.login_server
}

output "acr_admin_username" {
  description = "ACR admin username for Jenkins credentials"
  value       = module.acr.admin_username
  sensitive   = true
}

output "acr_admin_password" {
  description = "ACR admin password for Jenkins credentials"
  value       = module.acr.admin_password
  sensitive   = true
}

output "kube_config_raw" {
  description = "Raw kubeconfig for AKS — paste into Jenkins kubeconfig credential"
  value       = module.aks.kube_config_raw
  sensitive   = true
}

output "cluster_name" {
  value = module.aks.cluster_name
}

output "resource_group_name" {
  value = module.aks.resource_group_name
}
