pipelineJob('import-performance-dynamic-dataset') {

  displayName 'Import performance test on dynamic dataset'
  description 'Test Optimize Import performance against dynamically generated dataset.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/import_dynamic_data_performance.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for performance tests.')
    stringParam('POSTGRES_VERSION', '9.6-alpine', 'Postgres version to use.')
    stringParam('CAMBPM_VERSION', '7.11.0-alpha4', 'Camunda BPM version to use.')
    stringParam('ES_VERSION', '6.2.0', 'Elasticsearch version to use.')
  }

  triggers {
    cron('H 1 * * *')
  }
}