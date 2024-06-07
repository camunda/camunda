terraform {
  required_version = "~> 1.8.0"

  required_providers {
    github = {
      source  = "integrations/github"
      version = "~> 6.0"
    }
  }
}

provider "github" {
  owner = "camunda"
}

data "github_repository" "camunda" {
  full_name = "camunda/camunda"
}
