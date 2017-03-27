pipelineJob('Performance') {
  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/performance.groovy'))
      sandbox()
    }
  }

  jdk '(Default)'

  triggers {
    githubPush()
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