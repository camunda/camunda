pipelineJob('query-performance-tests') {

  displayName 'Query performance test on large static dataset'
  description 'Test Optimize Query Performance against a large static dataset. This ensures that even with a large dataset the query do not take too long.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/query_performance.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for performance tests.')
    stringParam('CAMBPM_VERSION', '7.11.0', 'Camunda BPM version to use.')
    stringParam('ES_VERSION', '6.2.0', 'Elasticsearch version to use.')
  }

  triggers {
    cron('H 2 * * *')
  }
}