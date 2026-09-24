variable "name_prefix" {
  type        = string
  description = "Unique prefix for all resources created by this test run"
}

variable "primary_region" {
  type    = string
  default = "eu-west-1"
}

variable "secondary_region" {
  type    = string
  default = "eu-west-2"
}

variable "master_username" {
  type    = string
  default = "camunda"
}

variable "master_password" {
  type      = string
  sensitive = true
}

variable "database_name" {
  type    = string
  default = "CAMUNDA"
}

variable "engine_version" {
  type        = string
  description = "RDS for Oracle Enterprise Edition 19c engine version"
}

variable "instance_class" {
  type    = string
  default = "db.t3.medium"
}

variable "allocated_storage" {
  type    = number
  default = 20
}
