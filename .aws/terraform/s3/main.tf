resource "aws_s3_bucket" "camunda-optimize-s3" {
  bucket = "camunda-optimize-terraform"
  acl = "private"

  versioning {
    enabled = true
  }

  tags {
    Name = "${data.terraform_remote_state.global.project_name} - ${data.terraform_remote_state.global.env} - S3 Terraform State"
    Env = "${data.terraform_remote_state.global.env}"
    Managed = "terraform"
  }
}
