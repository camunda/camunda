def JOBS = [
  [name: 'aws-setup-rds-and-ec2-instance', downstream: 'aws-upgrade-db-schema'],
  [name: 'aws-upgrade-db-schema', downstream: 'aws-provision-camunda'],
  [name: 'aws-provision-camunda', downstream: '']
]

def sshKeyId = 'jenkins-optimize-aws-ssh'
def writeVaultPasswordFile = '''
echo ${OPTIMIZE_VAULT_SECRET} > ${WORKSPACE}/.vault_password
'''

def job = createJobWithCommonProperties(this, JOBS[0].name)
job.with {

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


job = createJobWithCommonProperties(this, JOBS[1].name)
job.with {

  displayName 'AWS - Upgrade DB Schema'
  description 'Upgrades the DB schema to latest available version of Camunda BPM Platform.'

  steps {
    shell writeVaultPasswordFile
    shell readFileFromWorkspace('.aws/scripts/upgrade-db-schema.sh')
  }

  wrappers {
    sshAgent (sshKeyId)

    credentialsBinding {
      string('OPTIMIZE_VAULT_SECRET', 'optimize-vault-secret')
      usernamePassword('AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'jenkins-optimize-aws-secrets')
    }
  }

  publishers {
    downstream(JOBS[1].downstream)
  }
}


job = createJobWithCommonProperties(this, JOBS[2].name)
job.with {

  displayName 'AWS - Provision Camunda BPM and Optimize'
  description 'Provision the Camunda BPM Platform and Optimize on EC2 instance.'

  steps {
    shell writeVaultPasswordFile
    shell readFileFromWorkspace('.aws/scripts/provision-camunda.sh')
  }

  wrappers {
    sshAgent (sshKeyId)

    credentialsBinding {
      string('OPTIMIZE_VAULT_SECRET', 'optimize-vault-secret')
      usernamePassword('AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'jenkins-optimize-aws-secrets')
    }
  }
}



job = createJobWithCommonProperties(this, 'aws-execute-single-terraform-component')
job.with {

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



def static createJobWithCommonProperties(dslFactory, name) {
  def githubOrga = 'camunda'
  def gitRepository = 'camunda-optimize'
  def gitBranch = 'master'

  def job = dslFactory.freeStyleJob(name, {})
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

    publishers {
      extendedEmail {
        recipientList('optimize@camunda.com')
        triggers {
          statusChanged {
            sendTo {
              requester()
            }
          }
        }
      }
    }

  }

  job
}
