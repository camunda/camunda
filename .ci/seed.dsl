def githubOrga = 'camunda-cloud'
def gitRepository = 'tasklist'
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

def seedJob = job('seed-job-tasklist') {

  displayName 'Seed Job Zeebe Tasklist'
  description 'JobDSL Seed Job for Zeebe Tasklist'

  scm {
    git {
      remote {
        github "${githubOrga}/${gitRepository}", 'https'
        credentials 'github-cloud-zeebe-tasklist-app'
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


multibranchPipelineJob('zeebe-tasklist') {

  displayName 'Zeebe Tasklist'
  description 'MultiBranchJob for Zeebe Tasklist'

  branchSources {
    github {
      id 'zeebe-tasklist'
      repoOwner githubOrga
      repository gitRepository
      scanCredentialsId 'github-cloud-zeebe-tasklist-app'
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
