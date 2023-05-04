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


job('seed-job-optimize') {
  displayName 'Seed Job Optimize'
  description 'JobDSL Seed Job for Camunda Optimize'

  scm {
    git {
      remote {
        github "${githubOrga}/${gitRepository}", 'https'
        credentials 'github-optimize-app'
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


multibranchPipelineJob('camunda-optimize') {

  displayName 'Camunda Optimize'
  description 'MultiBranchJob for Camunda Optimize'

  branchSources {
    branchSource {
      source {
        github {
          id 'optimize-repo'
          repoOwner githubOrga
          repository gitRepository
          credentialsId 'github-optimize-app'
          repositoryUrl "https://github.com/${githubOrga}/${gitRepository}"
          configuredByUrl false
          traits {
            // Discover branches.
            // Strategy ID 3 => Discover all branches.
            gitHubBranchDiscovery {
              strategyId 3
            }

            // Discovers pull requests where the origin repository is the same as the target repository.
            // Strategy ID 2 => The current pull request revision.
            gitHubPullRequestDiscovery {
              strategyId 2
            }

            // Based on team usage and decision, only main branch, PRs, and selected branches will be part of CI.
            // That to avoid running the same CI job twice (one for branch and one for PR).
            headWildcardFilter {
              // Space-separated list of name patterns to consider.
              includes 'master PR-* CI-* maintenance/3.7 maintenance/3.8 maintenance/3.9 maintenance/3.10'
              excludes ''
            }

            // Disable sending Github status notifications in non-prod envs.
            if (ENVIRONMENT != 'prod') {
              githubSkipNotifications()
            }
          }
        }
      }
      buildStrategies {
        // Don't auto build discovered branches/PRs for non prod envs.
        if (ENVIRONMENT == 'prod') {
          // Builds regular branches whenever a change is detected.
          buildRegularBranches()

          // Builds change requests / pull requests.
          buildChangeRequests {
            ignoreTargetOnlyChanges false
            ignoreUntrustedChanges true
          }
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
