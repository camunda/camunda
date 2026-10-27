output "primary_endpoint" {
  value = aws_db_instance.primary.address
}

output "primary_identifier" {
  value = aws_db_instance.primary.identifier
}

output "replica_identifier" {
  value = aws_db_instance.replica.identifier
}

output "bastion_instance_id" {
  value = aws_instance.bastion.id
}

output "database_name" {
  value = upper(var.database_name)
}

output "database_username" {
  value     = var.master_username
  sensitive = true
}

output "db_port" {
  value = 1521
}
