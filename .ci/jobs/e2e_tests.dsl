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
    stringParam('ES_VERSION', '', 'Elasticsearch version to use, defaults to reading it from pom.xml.')
    stringParam('CAMBPM_VERSION', '', 'Camunda BPM version to use, defaults to reading it from pom.xml.')
  }

  triggers {
    cron('H 23 * * *')
  }
}
