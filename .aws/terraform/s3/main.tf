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

data "aws_iam_policy_document" "camunda-optimize-s3-caddy-access" {
    statement {
      sid = "Stmt1493724627726"
      effect = "Deny"
      principals {
        type = "AWS"
        identifiers = ["*"]
      }
      actions = [ "s3:*" ]
      condition {
        test = "StringNotLike"
        variable = "aws:userid"

        values = [
          "AROAJGHN5YHLQKYCWCGAO:*",
          "AIDAJWCXFYGVKQ266VJ3K",
          "912534604991"
        ]
      }
    }
}

# only allow access for optimize
resource "aws_s3_bucket_policy" "camunda-optimize-s3-bucket" {
  bucket = "${aws_s3_bucket.camunda-optimize-s3.id}"
  policy = "${data.aws_iam_policy_document.camunda-optimize-s3-caddy-access.json}"
}
