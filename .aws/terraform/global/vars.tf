variable "project_name" {
  default = "Camunda Optimize"
}

variable "env" {
  default = "stage"
}

variable "aws_region" {
  default = "eu-west-1"
}

variable "aws_profile" {
  default = ""
}

variable "aws_vpc_default" {
  default = "vpc-6e21ee0b"
}

variable "aws_eip_allocation_id_default" {
  default = "eipalloc-c03beca5"
}
