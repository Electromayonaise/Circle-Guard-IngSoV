environment         = "prod"
resource_group_name = "circleguard-prod-rg"
cluster_name        = "circleguard-prod-aks"
acr_name            = "circleguardprodacr"
location            = "eastus"
node_count          = 3
vm_size             = "Standard_B4ms"
namespaces          = ["master"]
