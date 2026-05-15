resource "azurerm_container_registry" "main" {
  name                = var.acr_name
  resource_group_name = var.resource_group_name
  location            = var.location
  sku                 = "Basic"
  admin_enabled       = true
}

resource "time_sleep" "wait_for_aks_identity" {
  create_duration = "30s"
  triggers = {
    aks_kubelet_id = var.aks_kubelet_id
  }
}

resource "azurerm_role_assignment" "aks_acr_pull" {
  principal_id                     = var.aks_kubelet_id
  role_definition_name             = "AcrPull"
  scope                            = azurerm_container_registry.main.id
  skip_service_principal_aad_check = true

  depends_on = [time_sleep.wait_for_aks_identity]
}
