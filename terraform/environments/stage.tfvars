environment         = "stage"
resource_group_name = "circleguard-stage-rg"
cluster_name        = "circleguard-stage-aks"
acr_name            = "circleguardstageacr"
location            = "eastus"
node_count          = 2
vm_size             = "Standard_B2s"
namespaces          = ["stage"]
