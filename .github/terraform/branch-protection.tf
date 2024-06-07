
resource "github_branch_protection" "main" {
  repository_id = data.github_repository.camunda.node_id

  pattern        = "main"
  enforce_admins = true

  required_pull_request_reviews {
    required_approving_review_count = 1
  }

  required_status_checks {
    contexts = [
      "Test summary",
      "Operate CI test summary",
      "Tasklist CI test summary",
      "SDK test summary",
    ]
  }

  # Merge queue cannot be configured via GitHub API yet, for updates see
  # https://github.com/integrations/terraform-provider-github/issues/1481

  # Require merge queue: YES
  # Only merge non-failing pull requests: YES
}


################################################################################
resource "github_branch_protection" "stable85" {
  repository_id = data.github_repository.camunda.node_id

  pattern        = "stable/8.5"
  enforce_admins = true

  required_pull_request_reviews {
    required_approving_review_count = 1
  }

  required_status_checks {
    contexts = [
      "Test summary",
      "SDK test summary",
    ]
  }

  # Merge queue cannot be configured via GitHub API yet, for updates see
  # https://github.com/integrations/terraform-provider-github/issues/1481

  # Require merge queue: YES
  # Only merge non-failing pull requests: YES
}

resource "github_branch_protection" "stable_operate85" {
  repository_id = data.github_repository.camunda.node_id

  pattern        = "stable/operate-8.5"
  enforce_admins = true

  required_pull_request_reviews {
    required_approving_review_count = 1
  }

  required_status_checks {
    contexts = [
      "Operate CI test summary",
    ]
  }

  # Merge queue cannot be configured via GitHub API yet, for updates see
  # https://github.com/integrations/terraform-provider-github/issues/1481

  # Require merge queue: YES
  # Only merge non-failing pull requests: YES
}



################################################################################
resource "github_branch_protection" "stable84" {
  repository_id = data.github_repository.camunda.node_id

  pattern        = "stable/8.4"
  enforce_admins = true

  required_pull_request_reviews {
    required_approving_review_count = 1
  }

  required_status_checks {
    contexts = [
      "Test summary",
    ]
  }

  # Merge queue cannot be configured via GitHub API yet, for updates see
  # https://github.com/integrations/terraform-provider-github/issues/1481

  # Require merge queue: YES
  # Only merge non-failing pull requests: YES
}


################################################################################
resource "github_branch_protection" "stable83" {
  repository_id = data.github_repository.camunda.node_id

  pattern        = "stable/8.3"
  enforce_admins = true

  required_pull_request_reviews {
    required_approving_review_count = 1
  }

  required_status_checks {
    contexts = [
      "Test summary",
    ]
  }

  # Merge queue cannot be configured via GitHub API yet, for updates see
  # https://github.com/integrations/terraform-provider-github/issues/1481

  # Require merge queue: YES
  # Only merge non-failing pull requests: YES
}


################################################################################
resource "github_branch_protection" "stable82" {
  repository_id = data.github_repository.camunda.node_id

  pattern        = "stable/8.2"
  enforce_admins = true

  required_pull_request_reviews {
    required_approving_review_count = 1
  }

  required_status_checks {
    contexts = [
      "Test summary",
    ]
  }

  # Merge queue cannot be configured via GitHub API yet, for updates see
  # https://github.com/integrations/terraform-provider-github/issues/1481

  # Require merge queue: YES
  # Only merge non-failing pull requests: YES
}
