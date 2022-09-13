pipelineJob('e2e_tests') {

  displayName 'E2E tests'
  description 'Run Optimize E2E tests with browserstack.'

  // By default, this job is disabled in non-prod envs.
  if (ENVIRONMENT != "prod") {
    disabled()
  }

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/e2e_tests.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', binding.variables.get('GIT_LOCAL_BRANCH', 'master'), 'Branch to use for E2E tests.')
    stringParam('ES_VERSION', '', 'Elasticsearch version to use, defaults to reading it from pom.xml.')
    stringParam('CAMBPM_VERSION', '', 'Camunda BPM version to use, defaults to reading it from pom.xml.')
  }

  // Disable cron testing envs, in case someone tests the job and forgets to disable it
    if (ENVIRONMENT == "prod") {
    properties {
      pipelineTriggers {
        triggers {
          cron {
            spec('H 23 * * 1-5')
          }
        }
      }
    }
  }
}
