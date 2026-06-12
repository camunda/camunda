# Ephemeral Aurora Global Database used by the AuroraAsyncReplicationIT acceptance test.
#
# Wraps the aurora-global module from camunda/camunda-deployment-references, which the
# workflow checks out next to this repository (path: camunda-deployment-references).
# Uses the default VPC of each region and adds a small SSM-managed bastion instance so
# the GitHub runner can reach the (non-public) Aurora endpoint through an SSM port
# forwarding session.
#
# State is local to the workflow run; the workflow destroys the stack in an always()
# cleanup step.

terraform {
  required_version = ">= 1.7"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0"
    }
    time = {
      source  = "hashicorp/time"
      version = ">= 0.9"
    }
  }
}

provider "aws" {
  alias  = "primary"
  region = var.primary_region
}

provider "aws" {
  alias  = "secondary"
  region = var.secondary_region
}

variable "name_prefix" {
  type        = string
  description = "Unique prefix for all resources (e.g. aurora-it-<run id>)"
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
  type      = string
  default   = "camunda_admin"
  sensitive = true
}

variable "master_password" {
  type      = string
  sensitive = true
}

variable "instance_class" {
  type    = string
  default = "db.r6g.large"
}

# Scaled 1 -> 0 -> 1 by the test to remove/restore the replica instance while
# keeping the secondary cluster (and storage replication) in place.
variable "secondary_num_instances" {
  type    = number
  default = 1
}

data "aws_vpc" "primary" {
  provider = aws.primary
  default  = true
}

data "aws_subnets" "primary" {
  provider = aws.primary
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.primary.id]
  }
}

data "aws_availability_zones" "primary" {
  provider = aws.primary
  state    = "available"
}

data "aws_vpc" "secondary" {
  provider = aws.secondary
  default  = true
}

data "aws_subnets" "secondary" {
  provider = aws.secondary
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.secondary.id]
  }
}

module "aurora" {
  source = "../../../camunda-deployment-references/aws/modules/aurora-global"

  providers = {
    aws.primary   = aws.primary
    aws.secondary = aws.secondary
  }

  global_cluster_identifier = "${var.name_prefix}-global"
  master_username           = var.master_username
  master_password           = var.master_password
  instance_class            = var.instance_class
  backup_retention_period   = 1
  iam_auth_enabled          = false

  primary_cluster_name       = "${var.name_prefix}-primary"
  primary_vpc_id             = data.aws_vpc.primary.id
  primary_subnet_ids         = data.aws_subnets.primary.ids
  primary_cidr_blocks        = [data.aws_vpc.primary.cidr_block]
  primary_availability_zones = slice(data.aws_availability_zones.primary.names, 0, 3)
  primary_num_instances      = 1

  secondary_cluster_name  = "${var.name_prefix}-secondary"
  secondary_vpc_id        = data.aws_vpc.secondary.id
  secondary_subnet_ids    = data.aws_subnets.secondary.ids
  secondary_cidr_blocks   = [data.aws_vpc.secondary.cidr_block]
  secondary_num_instances = var.secondary_num_instances

  tags = {
    Purpose = "aurora-async-replication-it"
    Name    = var.name_prefix
  }
}

################################
# SSM bastion (primary region) #
################################

data "aws_ami" "al2023" {
  provider    = aws.primary
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023*-x86_64"]
  }
}

resource "aws_iam_role" "bastion" {
  provider = aws.primary
  name     = "${var.name_prefix}-bastion"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })

  tags = {
    Name    = "${var.name_prefix}-bastion"
    Purpose = "aurora-async-replication-it"
  }
}

resource "aws_iam_role_policy_attachment" "bastion_ssm" {
  provider   = aws.primary
  role       = aws_iam_role.bastion.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "bastion" {
  provider = aws.primary
  name     = "${var.name_prefix}-bastion"
  role     = aws_iam_role.bastion.name

  tags = {
    Name    = "${var.name_prefix}-bastion"
    Purpose = "aurora-async-replication-it"
  }
}

resource "aws_security_group" "bastion" {
  provider    = aws.primary
  name        = "${var.name_prefix}-bastion"
  description = "Bastion for SSM port forwarding to Aurora"
  vpc_id      = data.aws_vpc.primary.id

  tags = {
    Name    = "${var.name_prefix}-bastion"
    Purpose = "aurora-async-replication-it"
  }

  # outbound only: 443 for the SSM agent, 5432 towards Aurora
  egress {
    from_port   = 443
    to_port     = 443
    protocol    = "TCP"
    cidr_blocks = ["0.0.0.0/0"]
    description = "SSM agent"
  }

  egress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "TCP"
    cidr_blocks = [data.aws_vpc.primary.cidr_block]
    description = "PostgreSQL to Aurora"
  }
}

resource "aws_instance" "bastion" {
  provider = aws.primary

  ami                         = data.aws_ami.al2023.id
  instance_type               = "t3.micro"
  subnet_id                   = data.aws_subnets.primary.ids[0]
  iam_instance_profile        = aws_iam_instance_profile.bastion.name
  vpc_security_group_ids      = [aws_security_group.bastion.id]
  associate_public_ip_address = true

  # The replica stop/start steps each re-run `terraform apply`, which re-reads the AMI
  # (most_recent = true) and subnet (aws_subnets ordering is not guaranteed stable) data
  # sources. A change in either forces a replacement of this instance — terminating the
  # bastion mid-test, which drops the live SSM session and makes the cached instance id
  # fail with "TargetNotConnected". Pin both so re-applies never recreate the bastion.
  lifecycle {
    ignore_changes = [ami, subnet_id]
  }

  tags = {
    Name    = "${var.name_prefix}-bastion"
    Purpose = "aurora-async-replication-it"
  }
}

output "primary_cluster_endpoint" {
  value = module.aurora.primary_cluster_endpoint
}

output "bastion_instance_id" {
  value = aws_instance.bastion.id
}
