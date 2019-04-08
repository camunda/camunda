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
        github 'zeebe-io/zeebe', 'ssh'
        credentials 'camunda-jenkins-github-ssh'
      }
      branch 'develop'
      extensions {
        localBranch 'master'
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
  }

  wrappers {
    timestamps()
    timeout {
      absolute 5
    }
  }

  logRotator(-1, 5, -1, 1)
}

queue(seedJob)
