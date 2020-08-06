pipelineJob('secured_es') {

  displayName 'Connect to secured Elasticsearch'
  description 'Verify connection to a secured ES instance'

  // By default, this job is disabled in non-prod envs.
  if (binding.variables.get("ENVIRONMENT") != "prod") {
    disabled()
  }

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/secured_es.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for the test.')
  }

  properties {
    pipelineTriggers {
      triggers {
        cron {
          spec('H 5 * * *')
        }
      }
    }
  }
}
