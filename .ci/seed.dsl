// vim: set filetype=groovy:

def dslScriptsToExecute = '''\
.ci/jobs/*.dsl
'''

def dslScriptPathToMonitor = '''\
.ci/jobs/.*\\.dsl
.ci/pipelines/.*\\.groovy
'''

def seedJob = job('seed-job-zeebe') {

  displayName 'Seed Job Zeebe'
  description 'JobDSL Seed Job for Zeebe'

  scm {
    git {
      remote {
        github 'camunda/zeebe', 'https'
        credentials 'github-camunda-zeebe-app'
      }
      branch 'main'
      extensions {
        localBranch 'main'
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
  }

  wrappers {
    timestamps()
    timeout {
      absolute 5
    }
  }

  logRotator {
    daysToKeep(-1)
    numToKeep(5)
    artifactDaysToKeep(-1)
    artifactNumToKeep(1)
  }
}

queue(seedJob)
