def githubOrga = 'camunda'
def gitRepository = 'camunda-optimize'
def gitBranch = 'master'

pipelineJob('Engine 7.7') {
  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/engine-7.7.groovy'))
      sandbox()
    }
  }

  jdk '(Default)'

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
}