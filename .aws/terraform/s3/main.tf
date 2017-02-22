resource "aws_s3_bucket" "camunda-optimize-s3" {
  bucket = "camunda-optimize-terraform"
  acl = "private"

  versioning {
    enabled = true
  }

  tags {
    Name = "${var.project_name} - ${var.env} - S3 Terraform State"
    Env = "${var.env}"
    Managed = "terraform"
  }
}
