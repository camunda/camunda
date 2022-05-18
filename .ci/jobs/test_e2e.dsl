pipelineJob('test_e2e') {

  disabled(ENVIRONMENT != 'prod')
  displayName 'e2e'
  description '''e2e'''

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/test_e2e.groovy'))
      sandbox()
    }
  }

  properties {
    pipelineTriggers {
      triggers {
        // cron {
        //   spec('H 5 * * *')
        // }
      }
    }
  }
}
