provider "aws" {
  region  = "eu-west-1"
  profile = "${data.terraform_remote_state.global.aws_profile}"
}
