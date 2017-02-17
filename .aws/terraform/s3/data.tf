# fetch db outputs from s3
data "terraform_remote_state" "global" {
  backend = "s3"

  config {
    bucket = "camunda-optimize-terraform"
    key    = "global/terraform.tfstate"
    region = "eu-west-1"
  }
}
