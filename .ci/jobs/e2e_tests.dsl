pipelineJob('e2e_tests') {

  displayName 'E2E tests'
  description 'Run Optimize E2E tests with browserstack.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/e2e_tests.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for E2E tests.')
    stringParam('ES_VERSION', '6.2.0', 'Elasticsearch version to use.')
    stringParam('CAMBPM_VERSION', '7.11.0', 'Camunda BPM version to use.')
  }

  triggers {
    cron('H 23 * * *')
  }
}
