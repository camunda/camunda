def githubOrga = 'camunda'
def gitRepository = 'camunda-optimize'
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

def seedJob = job('seed-job-optimize') {

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
      ignoreMissingFiles(false)
      unstableOnDeprecation(true)
      sandbox(true)
    }
    systemGroovyCommand(setBrokenViewAsDefault) {
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

// By default, this job is enabled for prod env only.
if (binding.variables.get("ENVIRONMENT") == "prod") {
  multibranchPipelineJob('camunda-optimize') {
  
    displayName 'Camunda Optimize'
    description 'MultiBranchJob for Camunda Optimize'
  
    branchSources {
      github {
        id 'optimize-repo'
        repoOwner githubOrga
        repository gitRepository
        scanCredentialsId 'github-optimize-app'
        // Discover branches => All branches
        buildOriginBranch()
        // Discover pull requests from origin => The current pull request revision
        buildOriginPRHead()
        // Based on team usage and decision, only main branch, PRs, and selected branches will be part of CI.
        // That to avoid running the same CI job twice (one for branch and one for PR).
        includes 'master PR-* CI-*'
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
}
