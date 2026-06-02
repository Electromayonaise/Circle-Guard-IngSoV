resource "azurerm_resource_group" "main" {
  name     = var.resource_group_name
  location = var.location

  tags = {
    environment = var.environment
    project     = "circleguard"
  }
}

resource "azurerm_kubernetes_cluster" "main" {
  name                = var.cluster_name
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  dns_prefix          = var.cluster_name
  kubernetes_version  = "1.34"

  default_node_pool {
    name                        = "default"
    node_count                  = var.node_count
    vm_size                     = var.vm_size
    os_disk_size_gb             = 128
    max_pods                    = 250
    temporary_name_for_rotation = "temppool"
  }

  network_profile {
    network_plugin      = "azure"
    network_plugin_mode = "overlay"
    pod_cidr            = "10.244.0.0/16"
    service_cidr        = "10.0.0.0/16"
    dns_service_ip      = "10.0.0.10"
    load_balancer_sku   = "standard"
  }

  oidc_issuer_enabled       = true
  workload_identity_enabled = true

  identity {
    type = "SystemAssigned"
  }

  tags = {
    environment = var.environment
    project     = "circleguard"
  }
}

resource "azurerm_kubernetes_cluster_node_pool" "jenkins" {
  name                  = "jenkins"
  kubernetes_cluster_id = azurerm_kubernetes_cluster.main.id
  vm_size               = "Standard_D2s_v3"
  node_count            = 1
  os_disk_size_gb       = 128
  max_pods              = 250
  node_taints           = ["dedicated=jenkins:NoSchedule"]
  node_labels           = { dedicated = "jenkins" }

  tags = {
    environment = var.environment
    project     = "circleguard"
  }
}
