
def githubOrga = 'camunda'
def repository = 'camunda-optimize'
def gitBranch = 'master'

def dslScriptsToExecute = '''\
.ci/jobs/**/*.dsl
.ci/views/common/**/*.dsl
.ci/views/release/**/*.dsl
'''

def setBrokenPageAsDefault = '''\
import jenkins.model.Jenkins

def jenkins = Jenkins.instance
def broken = jenkins.getView('Broken')
if (broken) {
  jenkins.setPrimaryView(broken)
}
'''

job('seed-job-ci') {

  scm {
    git {
      remote {
        github "${githubOrga}/${repository}", 'ssh'
        credentials 'camunda-jenkins-github-ssh'
      }
      branch gitBranch
      extensions {
        localBranch gitBranch
        pathRestriction {
          includedRegions(dslScriptsToExecute)
          excludedRegions('')
        }
      }
    }
  }

  triggers {
    scm 'H/5 * * * *'
  }

  label 'master'
  jdk '(Default)'

  steps {
    jobDsl {
      targets(dslScriptsToExecute)
      removedJobAction('DELETE')
      removedViewAction('DELETE')
      failOnMissingPlugin(true)
      unstableOnDeprecation(true)
    }
    systemGroovyCommand(setBrokenPageAsDefault)
  }

  wrappers {
    timestamps()
    timeout {
      absolute 5
    }
  }

  publishers {
    extendedEmail {
      triggers {
        statusChanged {
          sendTo {
            culprits()
            requester()
          }
        }
      }
    }
  }

  logRotator(-1, 5, -1, 1)

}
