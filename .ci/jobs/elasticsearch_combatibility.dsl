pipelineJob('elasticsearch_compatibility') {

  displayName 'Elasticsearch compatibility test'
  description 'Run Optimize IT test suite against different elasticsearch versions.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/elasticsearch_compatibility.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for elasticsearch compatibility tests.')
    stringParam('CAMBPM_VERSION', '7.11.0-alpha4', 'Camunda BPM version to use.')
  }

  triggers {
    cron('H 1 * * *')
  }
}