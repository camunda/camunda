pipelineJob('browserstack/test_e2e_browserstack') {

  disabled(ENVIRONMENT != 'prod')
  displayName 'e2e browserstack'
  description '''Running our e2e tests remotely in Browserstack'''

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/test_e2e_browserstack.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('OPERATE_BROWSERSTACK_BROWSER', 'browserstack:Firefox', 'The ID that determines which browser to use for E2E tests in Browserstack.')
    stringParam('OPERATE_BRANCH', 'master', 'Operate branch to use for E2E test code.')
  }
}
