#
# DB instance
#
resource "aws_db_instance" "optimize_db" {
  identifier             = "camunda-optimize-db-${data.terraform_remote_state.global.env}"
  storage_type           = "gp2"
  instance_class         = "db.t2.micro"
  port                   = "${var.db_port}"
  vpc_security_group_ids = ["${aws_security_group.optimize_db.id}"]
  parameter_group_name   = "default.postgres9.4"

  snapshot_identifier = "${var.db_latest_snapshot}"

  multi_az                = false
  backup_retention_period = 0

  apply_immediately = true

  tags {
    Name    = "${data.terraform_remote_state.global.project_name} - ${data.terraform_remote_state.global.env} - DB"
    Env     = "${data.terraform_remote_state.global.env}"
    Managed = "terraform"
  }
}

#
# DB Security
#
resource "aws_security_group" "optimize_db" {
  name   = "${data.terraform_remote_state.global.project_name} - ${data.terraform_remote_state.global.env} - DB Security Group"
  vpc_id = "${data.terraform_remote_state.global.vpc_default_id}"

  tags {
    Name = "${data.terraform_remote_state.global.project_name} - ${data.terraform_remote_state.global.env} - DB Security Group"
    Env     = "${data.terraform_remote_state.global.env}"
    Managed = "terraform"
  }
}

module "optimize_db_security_in" {
  source = "../modules/security_rule_global"
  group  = "${aws_security_group.optimize_db.id}"
  type   = "ingress"
  ports  = [5432]
}


#
# Optimize instance
#
resource "aws_instance" "optimize" {
  ami = "${data.aws_ami.ubuntu.id}"
  instance_type = "m4.large"
  key_name = "${data.terraform_remote_state.global.jenkins_aws_key_id}"
  iam_instance_profile = "${aws_iam_instance_profile.optimize_s3_iam_instance_profile.name}"
  vpc_security_group_ids = ["${aws_security_group.optimize.id}"]

  associate_public_ip_address = true

  tags {
    Name = "${data.terraform_remote_state.global.project_name} - ${data.terraform_remote_state.global.env} - Optimize Distro"
    Env = "${data.terraform_remote_state.global.env}"
    Managed = "terraform"
  }
}

#
# S3 bucket access IAM roles etc. for Optimize instance
#
resource "aws_iam_role" "optimize_s3_role" {
    name = "optimize_s3_role"
    assume_role_policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": "sts:AssumeRole",
            "Principal": {
               "Service": "ec2.amazonaws.com"
            },
            "Effect": "Allow",
            "Sid": ""
        }
    ]
}
EOF
}

resource "aws_iam_instance_profile" "optimize_s3_iam_instance_profile" {
    name = "optimize_s3_iam_instance_profile"
    roles = ["${aws_iam_role.optimize_s3_role.name}"]
}

resource "aws_iam_role_policy" "optimize_s3_role_policy" {
    name = "optimize_s3_role_policy"
    role = "${aws_iam_role.optimize_s3_role.id}"
    policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "Stmt1488533493102",
      "Action": "s3:*",
      "Effect": "Allow",
      "Resource": [
        "arn:aws:s3:::camunda-optimize-terraform/caddy",
        "arn:aws:s3:::camunda-optimize-terraform/caddy/*"
      ]
    },
    {
      "Sid": "Stmt1488545442681",
      "Action": [
        "s3:ListBucket"
      ],
      "Effect": "Allow",
      "Resource": "arn:aws:s3:::camunda-optimize-terraform"
    }
  ]
}
EOF
}



# Assign EIP for Optimize instance
resource "aws_eip_association" "camunda_optimize_eip" {
  instance_id = "${aws_instance.optimize.id}"
  allocation_id = "${data.terraform_remote_state.global.aws_eip_allocation_id_default}"
}

#
# Optimize instance Security
#
resource "aws_security_group" "optimize" {
  name   = "${data.terraform_remote_state.global.project_name} - ${data.terraform_remote_state.global.env} - Optimize Security Group"
  vpc_id = "${data.terraform_remote_state.global.vpc_default_id}"

  tags {
    Name = "${data.terraform_remote_state.global.project_name} - ${data.terraform_remote_state.global.env} - Optimize Security Group"
    Env = "${data.terraform_remote_state.global.env}"
    Managed = "terraform"
  }
}

module "optimize_security_in_hq" {
  source = "../modules/security_rule_global"
  group  = "${aws_security_group.optimize.id}"
  type   = "ingress"
  ports  = [22]
  cidr_blocks = ["178.19.212.50/32"]
}

module "optimize_security_in_public" {
  source = "../modules/security_rule_global"
  group  = "${aws_security_group.optimize.id}"
  type   = "ingress"
  ports  = [80,443]
}

module "optimize_security_out" {
  source = "../modules/security_rule_global"
  group  = "${aws_security_group.optimize.id}"
  type   = "egress"
  ports  = [80,443]
}

module "optimize_security_ldaps" {
  source = "../modules/security_rule_global"
  group  = "${aws_security_group.optimize.id}"
  type   = "egress"
  ports  = [636]
  cidr_blocks = ["91.109.21.65/32"]
}

module "optimize_to_optimize_db" {
  source = "../modules/security_rule_interconnect"
  from   = "${aws_security_group.optimize.id}"
  to     = "${aws_security_group.optimize_db.id}"
  ports  = [5432]
}
