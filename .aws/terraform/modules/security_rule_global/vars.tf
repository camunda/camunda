variable "group" {}

variable "type" {}

variable "ports" {
  type = "list"
}

variable "protocol" {
  default = "tcp"
}

variable "cidr_blocks" {
  default = ["0.0.0.0/0"]
}
