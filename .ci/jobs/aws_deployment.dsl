def githubOrga = 'camunda'
def gitRepository = 'camunda-optimize'
def gitBranch = 'master'

freeStyleJob('aws-create-database-from-snapshot') {

  displayName 'AWS - Create Database from Snapshot'
  description 'Creates the Camunda Optimize database from latest snapshot of Marketing\'s Production database.'

  parameters {
    booleanParam('APPLY', 'true', 'Set to true to apply the changes Terraform suggests.')
    booleanParam('DESTROY', 'false', 'Set to true to destroy the instance before Terraform applies changes.')
  }

  scm {
    git {
      remote {
        github "${githubOrga}/${gitRepository}", 'ssh'
        credentials 'camunda-jenkins-github-ssh'
      }
      branch gitBranch
      extensions {
        cleanBeforeCheckout()
        localBranch gitBranch
      }
    }
  }

  label 'opstools'
  jdk 'jdk-8-latest'

  steps {
    shell readFileFromWorkspace('.aws/terraform/scripts/create-rds-instance-from-latest-snapshot.sh')
  }

  triggers {
    cron('0 0 4 ? * MON-FRI *')
  }

  wrappers {
    timestamps()

    timeout {
      noActivity 15
    }

    credentialsBinding {
      usernamePassword('AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'jenkins-optimize-aws-credentials')
    }
  }

  blockOnUpstreamProjects()
  logRotator(-1, 10, -1, 1)
}
