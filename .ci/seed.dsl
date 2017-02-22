def githubOrga = 'camunda'
def gitRepository = 'camunda-optimize'
def gitBranch = 'master'

def dslScriptsToExecute = '''\
.ci/jobs/*.dsl
.ci/views/*.dsl
'''

def dslScriptPathToMonitor = '''\
.ci/jobs/.*\\.dsl
.ci/views/.*\\.dsl
'''

def setBrokenPageAsDefault = '''\
import jenkins.model.Jenkins

def jenkins = Jenkins.instance
def broken = jenkins.getView('Broken')
if (broken) {
  jenkins.setPrimaryView(broken)
}
'''

job('seed-job-optimize') {

  displayName 'Seed Job Optimize'
  description 'JobDSL Seed Job for Camunda Optimize'

  scm {
    git {
      remote {
        github "${githubOrga}/${gitRepository}", 'ssh'
        credentials 'camunda-jenkins-github-ssh'
      }
      branch gitBranch
      extensions {
        localBranch gitBranch
        pathRestriction {
          includedRegions(dslScriptPathToMonitor)
          excludedRegions('')
        }
      }
    }
  }

  triggers {
    githubPush()
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



multibranchPipelineJob('camunda-optimize') {

  displayName 'Camunda Optimize'
  description 'MultiBranchJob for Camunda Optimize'

  branchSources {
    github {
      repoOwner githubOrga
      repository gitRepository
      scanCredentialsId 'camunda-jenkins-github'
      excludes 'noci-*'
    }
  }

  orphanedItemStrategy {
    discardOldItems {
      daysToKeep 0
    }
  }
}
