def githubOrga = 'camunda'
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
        credentials 'github-tasklist-app'
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

  if (ENVIRONMENT == 'prod') {
    triggers {
      githubPush()
    }
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
    branchSource {
      source {
        github {
          id 'zeebe-tasklist'
          repoOwner githubOrga
          repository gitRepository
          credentialsId 'github-tasklist-app'
          repositoryUrl "https://github.com/${githubOrga}/${gitRepository}"
          configuredByUrl false
          traits {
            // Discover branches.
            // Strategy ID 3 => Discover all branches.
            gitHubBranchDiscovery {
              strategyId 3
            }

            // Disable sending Github status notifications in non-prod envs.
            if (ENVIRONMENT != 'prod') {
              notificationsSkip()
            }
          }
        }
      }
      buildStrategies {
        // Don't auto build discovered branches for non prod envs.
        if (ENVIRONMENT == 'prod') {
          // Builds regular branches whenever a change is detected.
          buildRegularBranches()
        }
      }
      strategy {
        allBranchesSame {
          props {
            if (ENVIRONMENT != 'prod') {
              // Suppresses the normal SCM commit trigger coming from branch indexing.
              suppressAutomaticTriggering()
            }
          }
        }
      }
    }
  }

  orphanedItemStrategy {
    discardOldItems {
      daysToKeep 1
    }
  }

  triggers {
    periodicFolderTrigger {
      interval '1d'
    }
  }
}
