resource "kubernetes_namespace" "namespaces" {
  for_each = toset(var.namespaces)

  metadata {
    name = each.value
    labels = {
      project     = "circleguard"
      environment = each.value
    }
  }
}
