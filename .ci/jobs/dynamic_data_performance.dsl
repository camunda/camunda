pipelineJob('performance-dynamic-dataset') {

  displayName 'Performance test on dynamic dataset'
  description 'Test Optimize Performance against dynamically generated dataset.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/dynamic_data_performance.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for performance tests.')
    stringParam('POSTGRES_VERSION', '9.6-alpine', 'Postgres version to use.')
    stringParam('CAMBPM_VERSION', '7.10.0-SNAPSHOT', 'Camunda BPM version to use.')
    stringParam('ES_VERSION', '6.0.0', 'Elasticsearch version to use.')
  }

  triggers {
    cron('H 3 * * *')
  }
}