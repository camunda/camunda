pipelineJob('upgrade-performance-large-static-dataset') {

  displayName 'Upgrade performance test on large static dataset'
  description 'Test Optimize upgrade performance against a large static dataset.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/upgrade_data_performance_large.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for performance tests.')
    stringParam('CAMBPM_VERSION', '7.11.0', 'Camunda BPM version to use.')
    stringParam('ES_VERSION', '6.2.0', 'Elasticsearch version to use.')
    stringParam('UPGRADE_TIMEOUT', '1800', 'Timeout for the upgrade to complete')
  }

  triggers {
    cron('H 4 * * *')
  }
}