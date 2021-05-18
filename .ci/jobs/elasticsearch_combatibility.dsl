pipelineJob('elasticsearch_compatibility') {

  displayName 'Elasticsearch compatibility test'
  description 'Runs IT suite with different supported Elasticsearch versions to check compatibility.'

  // By default, this job is disabled in non-prod envs.
  if (binding.variables.get("ENVIRONMENT") != "prod") {
    disabled()
  }

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/elasticsearch_compatibility.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for ITs.')
    stringParam('CAMBPM_VERSION', '', 'Camunda BPM version to use, defaults to reading it from pom.xml.')
  }

  properties {
    pipelineTriggers {
      triggers {
        cron {
          spec('H 2 * * *')
        }
      }
    }
  }
}
