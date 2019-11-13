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
    stringParam('POSTGRES_VERSION', '11.2', 'Postgres version to use.')
    stringParam('CAMBPM_VERSION', '', 'Camunda BPM version to use, defaults to reading it from pom.xml.')
    stringParam('ES_VERSION', '', 'Elasticsearch version to use, defaults to reading it from pom.xml.')
  }

  triggers {
    cron('H 1 * * *')
  }
}