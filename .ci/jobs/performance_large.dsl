pipelineJob('performance-large-dataset') {

  displayName 'Performance Large Dataset'
  description 'Test Optimize Performance against large dataset.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/performance_large.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for performance tests.')
    stringParam('CAMBPM_VERSION', '7.10.0-SNAPSHOT', 'Camunda BPM version to use.')
    stringParam('ES_VERSION', '6.0.0', 'Elasticsearch version to use.')
  }

  jdk '(Default)'

  triggers {
    cron('H 3 * * *')
  }
}