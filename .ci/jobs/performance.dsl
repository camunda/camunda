def githubOrga = 'camunda'
def gitRepository = 'camunda-optimize'
def gitBranch = 'master'

pipelineJob('Performance') {
  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/performance.groovy'))
      sandbox()
    }
  }

  jdk '(Default)'

  triggers {
    upstream('camunda-optimize/master', 'SUCCESS')
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
}