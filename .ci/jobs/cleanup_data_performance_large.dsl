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
    choiceParam('SQL_DUMP', ['optimize_large_data-performance.sqlc', 'optimize_large_data-stage.sqlc'])
    stringParam('CLEANUP_TIMEOUT_MINUTES', '60', 'Time limit for a cleanup run to finish')
  }

  triggers {
    cron('H 5 * * *')
  }
}