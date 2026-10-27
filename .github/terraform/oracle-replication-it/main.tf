locals {
  primary_identifier = substr("${var.name_prefix}-oracle-primary", 0, 63)
  replica_identifier = substr("${var.name_prefix}-oracle-replica", 0, 63)
  option_group_name  = substr("${var.name_prefix}-oracle-nne", 0, 64)
  engine_major       = split(".", var.engine_version)[0]
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

data "aws_ami" "al2023" {
  provider    = aws.primary
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023*-x86_64"]
  }
}

resource "aws_db_option_group" "primary" {
  provider = aws.primary

  name                     = local.option_group_name
  option_group_description = "Oracle native network encryption for ${var.name_prefix}"
  engine_name              = "oracle-ee"
  major_engine_version     = local.engine_major

  option {
    option_name = "NATIVE_NETWORK_ENCRYPTION"

    option_settings {
      name  = "SQLNET.ENCRYPTION_SERVER"
      value = "REQUIRED"
    }
    option_settings {
      name  = "SQLNET.ENCRYPTION_TYPES_SERVER"
      value = "AES256,AES192"
    }
    option_settings {
      name  = "SQLNET.CRYPTO_CHECKSUM_SERVER"
      value = "REQUIRED"
    }
    option_settings {
      name  = "SQLNET.CRYPTO_CHECKSUM_TYPES_SERVER"
      value = "SHA256,SHA384,SHA512"
    }
  }
}

resource "aws_db_subnet_group" "primary" {
  provider   = aws.primary
  name       = "${local.primary_identifier}-subnets"
  subnet_ids = data.aws_subnets.primary.ids
}

resource "aws_db_subnet_group" "secondary" {
  provider   = aws.secondary
  name       = "${local.replica_identifier}-subnets"
  subnet_ids = data.aws_subnets.secondary.ids
}

resource "aws_security_group" "bastion" {
  provider = aws.primary

  name_prefix = "${local.primary_identifier}-bastion-"
  description = "SSM bastion for Oracle async replication acceptance tests"
  vpc_id      = data.aws_vpc.primary.id

  egress {
    description = "SSM"
    protocol    = "tcp"
    from_port   = 443
    to_port     = 443
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Oracle primary"
    protocol    = "tcp"
    from_port   = 1521
    to_port     = 1521
    cidr_blocks = [data.aws_vpc.primary.cidr_block]
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group" "primary_db" {
  provider = aws.primary

  name_prefix = "${local.primary_identifier}-db-"
  description = "Oracle primary access from the SSM bastion"
  vpc_id      = data.aws_vpc.primary.id

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group_rule" "primary_db_from_bastion" {
  provider                 = aws.primary
  type                     = "ingress"
  security_group_id        = aws_security_group.primary_db.id
  source_security_group_id = aws_security_group.bastion.id
  protocol                 = "tcp"
  from_port                = 1521
  to_port                  = 1521
  description              = "Oracle JDBC from the SSM bastion"
}

resource "aws_security_group" "secondary_db" {
  provider = aws.secondary

  name_prefix = "${local.replica_identifier}-db-"
  description = "Oracle Data Guard replica access"
  vpc_id      = data.aws_vpc.secondary.id

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_iam_role" "bastion" {
  provider = aws.primary

  name = "${local.primary_identifier}-bastion"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "bastion_ssm" {
  provider   = aws.primary
  role       = aws_iam_role.bastion.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "bastion" {
  provider = aws.primary
  name     = "${local.primary_identifier}-bastion"
  role     = aws_iam_role.bastion.name
}

resource "aws_instance" "bastion" {
  provider = aws.primary

  ami                         = data.aws_ami.al2023.id
  instance_type               = "t3.micro"
  subnet_id                   = data.aws_subnets.primary.ids[0]
  iam_instance_profile        = aws_iam_instance_profile.bastion.name
  vpc_security_group_ids      = [aws_security_group.bastion.id]
  associate_public_ip_address = true

  lifecycle {
    ignore_changes = [ami, subnet_id]
  }

  depends_on = [aws_iam_role_policy_attachment.bastion_ssm]
}

resource "aws_db_instance" "primary" {
  provider = aws.primary

  identifier     = local.primary_identifier
  engine         = "oracle-ee"
  engine_version = var.engine_version
  license_model  = "bring-your-own-license"
  instance_class = var.instance_class

  allocated_storage = var.allocated_storage
  storage_type      = "gp3"
  storage_encrypted = false

  db_name                    = upper(var.database_name)
  username                   = var.master_username
  password                   = var.master_password
  port                       = 1521
  option_group_name          = aws_db_option_group.primary.name
  db_subnet_group_name       = aws_db_subnet_group.primary.name
  vpc_security_group_ids     = [aws_security_group.primary_db.id]
  publicly_accessible        = false
  multi_az                   = false
  backup_retention_period    = 1
  auto_minor_version_upgrade = false
  apply_immediately          = true
  skip_final_snapshot        = true
  deletion_protection        = false

  timeouts {
    create = "3h"
    update = "3h"
    delete = "3h"
  }
}

resource "aws_db_instance" "replica" {
  provider = aws.secondary

  identifier          = local.replica_identifier
  replicate_source_db = aws_db_instance.primary.arn
  replica_mode        = "mounted"
  instance_class      = var.instance_class

  db_subnet_group_name   = aws_db_subnet_group.secondary.name
  vpc_security_group_ids = [aws_security_group.secondary_db.id]
  publicly_accessible    = false
  multi_az               = false
  apply_immediately      = true
  skip_final_snapshot    = true
  deletion_protection    = false

  timeouts {
    create = "3h"
    update = "3h"
    delete = "3h"
  }
}
