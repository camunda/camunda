pipelineJob('test_e2e_browserstack') {

  displayName 'e2e browserstack'
  description '''Running our e2e tests remotely in Browserstack'''

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/test_e2e_browserstack.groovy'))
      sandbox()
    }
  }

  properties {
    pipelineTriggers {
      triggers {
        cron {
          spec('H 4 * * *')
        }
      }
    }
  }
}
