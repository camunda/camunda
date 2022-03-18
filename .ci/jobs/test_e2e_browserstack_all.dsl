pipelineJob('browserstack/test_e2e_browserstack_all') {

  disabled(ENVIRONMENT != 'prod')
  displayName 'e2e browserstack ALL'
  description '''Running our e2e tests remotely in Browserstack'''

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/test_e2e_browserstack_all.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('OPERATE_BRANCH', 'master', 'Operate branch to use for E2E test code.')
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
