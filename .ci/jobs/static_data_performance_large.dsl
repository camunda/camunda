pipelineJob('performance-large-static-dataset') {

  displayName 'Performance test on large static dataset'
  description 'Test Optimize Performance against a large static dataset.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/static_data_performance_large.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for performance tests.')
    stringParam('CAMBPM_VERSION', '7.10.0-SNAPSHOT', 'Camunda BPM version to use.')
    stringParam('ES_VERSION', '6.0.0', 'Elasticsearch version to use.')
  }

  triggers {
    cron('H 3 * * *')
  }
}