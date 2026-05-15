variable "namespace"        { type = string }
variable "service_name"     { type = string }
variable "service_port"     { type = number }
variable "acr_login_server" { type = string }

variable "image_tag" {
  type    = string
  default = "latest"
}

variable "replicas" {
  type    = number
  default = 1
}

variable "has_db" {
  type    = bool
  default = false
}

variable "db_name" {
  type    = string
  default = ""
}
