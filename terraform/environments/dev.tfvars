environment         = "dev"
resource_group_name = "circleguard-dev-rg"
cluster_name        = "circleguard-dev-aks"
acr_name            = "circleguarddevacr"
location            = "eastus"
node_count          = 2
vm_size             = "Standard_B2ms"

# Single-cluster design: dev, stage, and master all run as namespaces in this
# one cluster. stage/prod clusters were never provisioned.
namespaces          = ["dev", "stage", "master"]

deploy_shared_infra = true
