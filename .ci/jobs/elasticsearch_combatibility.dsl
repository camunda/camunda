pipelineJob('elasticsearch_compatibility') {

  displayName 'Elasticsearch compatibility test'
  description 'Runs IT suite with different supported Elasticsearch versions to check compatibility.'

  // By default, this job is disabled in non-prod envs.
  if (ENVIRONMENT != "prod") {
    disabled()
  }

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/elasticsearch_compatibility.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', binding.variables.get('GIT_LOCAL_BRANCH', 'master'), 'Branch to use for ITs.')
    stringParam('CAMBPM_VERSION', '', 'Camunda BPM version to use, defaults to reading it from pom.xml.')
  }

  // Disable cron testing envs, in case someone tests the job and forgets to disable it
  if (ENVIRONMENT == "prod") {
    properties {
      pipelineTriggers {
        triggers {
          cron {
            spec('H 2 * * 1-5')
          }
        }
      }
    }
  }
}
