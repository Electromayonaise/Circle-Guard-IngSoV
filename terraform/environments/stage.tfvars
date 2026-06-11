# NOTE: This file describes the INTENDED separate stage cluster which was never
# provisioned. The stage environment runs as a namespace inside circleguard-dev-aks.
# These values are here for reference only — use dev.tfvars for the real cluster.

environment         = "stage"
resource_group_name = "circleguard-stage-rg"
cluster_name        = "circleguard-stage-aks"
acr_name            = "circleguardstageacr"
location            = "eastus"
node_count          = 2
vm_size             = "Standard_B2s"
namespaces          = ["stage"]
