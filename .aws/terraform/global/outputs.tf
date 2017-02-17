output "project_name" {
  value = "${var.project_name}"
}

output "env" {
  value = "${var.env}"
}

output "aws_region" {
  value = "${var.aws_region}"
}

output "aws_profile" {
  value = "${var.aws_profile}"
}

output "vpc_default_id" {
  value = "${var.aws_vpc_default}"
}

output "jenkins_aws_key_id" {
  value = "${aws_key_pair.jenkins-optimize-aws.key_name}"
}
