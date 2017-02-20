def githubOrga = 'camunda'
def gitRepository = 'camunda-optimize'
def gitBranch = 'master'

def jobs = [
  [name: 'aws-setup-rds-and-ec2-instance', downstream: 'aws-upgrade-db-schema'],
  [name: 'aws-upgrade-db-schema', downstream: '']
]

freeStyleJob(jobs[0].name) {

  displayName 'AWS - Setup RDS and EC2 instance'
  description 'Creates the Camunda Optimize database from latest snapshot of Marketing\'s Production database and spins up the EC2 instance.'

  parameters {
    booleanParam('APPLY', true, 'Set to true to apply the changes Terraform suggests.')
    booleanParam('DESTROY', false, 'Set to true to destroy the instance before Terraform applies changes.')
    choiceParam('TF_LOG', ['INFO', 'DEBUG', 'TRACE', 'ERROR', 'WARN'], 'Sets the log level of Terraform. Useful for debugging purposes.')
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
  jdk '(Default)'

  steps {
    shell readFileFromWorkspace('.aws/scripts/create-optimize-db-and-instance.sh')
  }

  triggers {
    cron('H 3 * * 1-5')
  }

  wrappers {
    timestamps()

    timeout {
      noActivity 15
    }

    credentialsBinding {
      usernamePassword('AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'jenkins-optimize-aws-secrets')
    }
  }

  publishers {
    downstream(jobs[0].downstream)
  }

  blockOnUpstreamProjects()
  logRotator(-1, 10, -1, 1)
}



freeStyleJob(jobs[1].name) {

  displayName 'AWS - Upgrade DB Schema'
  description 'Upgrades the DB schema to latest available version of Camunda BPM Platform.'

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
  jdk '(Default)'

  steps {
    shell readFileFromWorkspace('.aws/scripts/upgrade-db-schema.sh')
  }

  wrappers {
    timestamps()

    timeout {
      noActivity 15
    }

    sshAgent ('jenkins-optimize-aws-ssh')

    credentialsBinding {
      usernamePassword('AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'jenkins-optimize-aws-secrets')
    }
  }

  blockOnUpstreamProjects()
  logRotator(-1, 10, -1, 1)
}
