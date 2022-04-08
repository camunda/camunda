pipelineJob('elasticsearch_aws_compatibility') {

  displayName 'Elasticsearch AWS compatibility test'
  description 'Run IT suite against elasticsearch on AWS.'

  // We are not currently officially supporting AWS, but may in future. In the meantime, this job is disabled
  disabled()

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/elasticsearch_aws_compatibility.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', binding.variables.get('GIT_LOCAL_BRANCH', 'master'), 'Branch to use for ITs.')
    stringParam('CAMBPM_VERSION', '', 'Camunda BPM version to use, defaults to reading it from pom.xml.')
  }

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
