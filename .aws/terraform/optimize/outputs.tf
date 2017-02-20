output "db-instance-identifier" {
  value = "${aws_db_instance.optimize_db.identifier}"
}

output "db-address" {
  value = "${aws_db_instance.optimize_db.address}"
}

output "db-port" {
  value = "${aws_db_instance.optimize_db.port}"
}

output "db-name" {
  value = "${aws_db_instance.optimize_db.name}"
}

output "db-endpoint" {
  value = "${aws_db_instance.optimize_db.endpoint}"
}

output "db-username" {
  value = "${aws_db_instance.optimize_db.username}"
}

output "db-password" {
  value = "${aws_db_instance.optimize_db.password}"
}

output "optimize-instance-identifier" {
  value = "${aws_instance.optimize.identifier}"
}

output "optimize-public-ip" {
  value = "${aws_instance.optimize.public_ip}"
}

output "optimize-private-ip" {
  value = "${aws_instance.optimize.private_ip}"
}
