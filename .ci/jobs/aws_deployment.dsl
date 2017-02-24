def githubOrga = 'camunda'
def gitRepository = 'camunda-optimize'
def gitBranch = 'master'

def JOBS = [
  [name: 'aws-setup-rds-and-ec2-instance', downstream: 'aws-upgrade-db-schema'],
  [name: 'aws-upgrade-db-schema', downstream: 'aws-provision-camunda-bpm'],
  [name: 'aws-provision-camunda-bpm', downstream: '']
]

def job = freeStyleJob(JOBS[0].name) {

  displayName 'AWS - Setup RDS and EC2 instance'
  description 'Creates the Camunda Optimize database from latest snapshot of Marketing\'s Production database and spins up the EC2 instance.'

  parameters {
    booleanParam('APPLY', true, 'Set to true to apply the changes Terraform suggests.')
    booleanParam('DESTROY', true, 'Set to true to destroy the instance before Terraform applies changes.')
    choiceParam('TF_LOG', ['INFO', 'DEBUG', 'TRACE', 'ERROR', 'WARN'], 'Sets the log level of Terraform. Useful for debugging purposes.')
  }

  steps {
    shell readFileFromWorkspace('.aws/scripts/create-optimize-db-and-instance.sh')
  }

  triggers {
    cron('H 3 * * 1-5')
  }

  publishers {
    downstream(JOBS[0].downstream)
  }

}
addCommonJobProperties(job)


job = freeStyleJob(JOBS[1].name) {

  displayName 'AWS - Upgrade DB Schema'
  description 'Upgrades the DB schema to latest available version of Camunda BPM Platform.'

  steps {
    shell readFileFromWorkspace('.aws/scripts/upgrade-db-schema.sh')
  }

  wrappers {
    sshAgent ('jenkins-optimize-aws-ssh')
  }

  publishers {
    downstream(JOBS[1].downstream)
  }
}
addCommonJobProperties(job)


job = freeStyleJob(JOBS[2].name) {

  displayName 'AWS - Provision Camunda BPM'
  description 'Provision the Camunda BPM Platform on EC2 instance.'

  steps {
    shell readFileFromWorkspace('.aws/scripts/provision-camunda-bpm.sh')
  }

  wrappers {
    sshAgent ('jenkins-optimize-aws-ssh')

    credentialsBinding {
      string('OPTIMIZE_VAULT_SECRET', 'optimize-vault-secret')
    }
  }
}
addCommonJobProperties(job)




job = freeStyleJob('aws-execute-single-terraform-component') {

  displayName 'AWS - Execute single Terraform component (manual)'
  description 'Allows to execute a single Terraform component on any branch.'

  parameters {
    booleanParam('APPLY', true, 'Set to true to apply the changes Terraform suggests.')
    booleanParam('DESTROY', false, 'Set to true to destroy the instance before Terraform applies changes.')
    choiceParam('TF_LOG', ['INFO', 'DEBUG', 'TRACE', 'ERROR', 'WARN'], 'Sets the log level of Terraform. Useful for debugging purposes.')
    choiceParam('COMPONENT', ['global', 'optimize', 's3'], 'Choose the component. Equals to subdir name inside Terraform folder hierachy.')
    stringParam('BRANCH', 'master', 'Choose the branch which should be used.')
  }

  steps {
    shell readFileFromWorkspace('.aws/terraform/scripts/run-terraform.sh')
  }
}
addCommonJobProperties(job)


def static addCommonJobProperties(job) {
  job.with {

    logRotator(-1, 10, -1, 1)
    blockOnUpstreamProjects()

    label 'opstools'
    jdk '(Default)'

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

    wrappers {
      timestamps()

      timeout {
        noActivity 15 * 60
      }

      credentialsBinding {
        usernamePassword('AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'jenkins-optimize-aws-secrets')
      }
    }

  }

  job
}
