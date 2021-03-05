def githubOrga = 'camunda-cloud'
def gitRepository = 'operate'
def gitBranch = 'master'

def dslScriptsToExecute = '''\
.ci/jobs/*.dsl
.ci/views/*.dsl
'''

def dslScriptPathToMonitor = '''\
.ci/jobs/.*\\.dsl
.ci/pipelines/.*
.ci/views/.*\\.dsl
'''

def setBrokenViewAsDefault = '''\
import jenkins.model.Jenkins

def jenkins = Jenkins.instance
def broken = jenkins.getView('Broken')
if (broken) {
  jenkins.setPrimaryView(broken)
}
'''

def seedJob = job('seed-job-operate') {

  displayName 'Seed Job Operate'
  description 'JobDSL Seed Job for Camunda Operate'

  scm {
    git {
      remote {
        github "${githubOrga}/${gitRepository}", 'https'
        credentials 'github-cloud-operate-app'
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
      ignoreMissingFiles(false)
      unstableOnDeprecation(true)
      sandbox(true)
    }
    systemGroovyCommand(setBrokenViewAsDefault){
      sandbox(true)
    }
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

queue(seedJob)


multibranchPipelineJob('camunda-operate') {

  displayName 'Camunda Operate'
  description 'MultiBranchJob for Camunda Operate'

  branchSources {
    github {
      id 'camunda-operate'
      repoOwner githubOrga
      repository gitRepository
      scanCredentialsId 'github-cloud-operate-app'
    }
  }

  orphanedItemStrategy {
    discardOldItems {
      daysToKeep(1)
    }
  }

  triggers {
    periodicFolderTrigger {
      interval('1d')
    }
  }
}
