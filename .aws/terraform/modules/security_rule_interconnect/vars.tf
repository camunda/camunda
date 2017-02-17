variable "from" {}

variable "to" {}

variable "ports" {
  type = "list"
}

variable "protocol" {
  default = "tcp"
}
