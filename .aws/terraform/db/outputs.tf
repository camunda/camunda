output "db-instance-identifier" {
  value = "${aws_db_instance.camunda_optimize_db.identifier}"
}

output "db-address" {
  value = "${aws_db_instance.camunda_optimize_db.address}"
}

output "db-port" {
  value = "${aws_db_instance.camunda_optimize_db.port}"
}

output "db-name" {
  value = "${aws_db_instance.camunda_optimize_db.name}"
}

output "db-endpoint" {
  value = "${aws_db_instance.camunda_optimize_db.endpoint}"
}

output "db-username" {
  value = "${aws_db_instance.camunda_optimize_db.username}"
}

output "db-password" {
  value = "${aws_db_instance.camunda_optimize_db.password}"
}
