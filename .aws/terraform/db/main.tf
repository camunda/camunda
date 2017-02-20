resource "aws_db_instance" "camunda_optimize_db" {
  identifier             = "camunda-optimize-db-${data.terraform_remote_state.global.env}"
  storage_type           = "gp2"
  instance_class         = "db.t2.micro"
  port                   = "${var.db_port}"
  vpc_security_group_ids = ["${aws_security_group.camunda_optimize_db.id}"]
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
# Security
#
resource "aws_security_group" "camunda_optimize_db" {
  name   = "${data.terraform_remote_state.global.project_name} - ${data.terraform_remote_state.global.env} - DB Security Group"
  vpc_id = "${data.terraform_remote_state.global.vpc_default_id}"

  tags {
    Name = "${data.terraform_remote_state.global.project_name} - ${data.terraform_remote_state.global.env} - DB Security Group"
    Env     = "${data.terraform_remote_state.global.env}"
    Managed = "terraform"
  }
}
