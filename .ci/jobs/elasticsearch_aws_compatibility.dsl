pipelineJob('elasticsearch_aws_compatibility') {

  displayName 'Elasticsearch AWS compatibility test'
  description 'Run Optimize IT test suite against elasticsearch on AWS.'

  // By default, this job is disabled in non-prod envs.
  if (binding.variables.get("ENVIRONMENT") != "prod") {
    disabled()
  }

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/elasticsearch_aws_compatibility.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for elasticsearch compatibility test.')
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
