variable "db_password" {
  description = "PostgreSQL password for SonarQube database"
  type        = string
  sensitive   = true
  default     = "sonarpass"
}
