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
    stringParam('CAMBPM_VERSION', '', 'Camunda BPM version to use, defaults to reading it from pom.xml.')
    stringParam('ES_VERSION', '', 'Elasticsearch version to use, defaults to reading it from pom.xml.')
  }

  triggers {
    cron('H 2 * * *')
  }
}