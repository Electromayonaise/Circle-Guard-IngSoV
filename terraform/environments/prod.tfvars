# NOTE: This file describes the INTENDED separate prod cluster which was never
# provisioned. The master (prod) environment runs as a namespace inside
# circleguard-dev-aks. These values are here for reference only — use
# dev.tfvars for the real cluster.

environment         = "prod"
resource_group_name = "circleguard-prod-rg"
cluster_name        = "circleguard-prod-aks"
acr_name            = "circleguardprodacr"
location            = "eastus"
node_count          = 3
vm_size             = "Standard_B4ms"
namespaces          = ["master"]
