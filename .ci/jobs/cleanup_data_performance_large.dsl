pipelineJob('cleanup-data-performance') {

  displayName 'History Cleanup Performance test'
  description 'Test Optimize History Cleanup Performance against a large static dataset.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/cleanup_data_performance.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for performance tests.')
    stringParam('CAMBPM_VERSION', '7.10.0', 'Camunda BPM version to use.')
    stringParam('ES_VERSION', '6.0.0', 'Elasticsearch version to use.')
  }

  triggers {
    cron('H 3 * * *')
  }
}