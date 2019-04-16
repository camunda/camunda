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
    stringParam('SQL_DUMP', 'optimize_large_data_10M_procinst_wo_unneded_data_7.10.0_schema.sqlc', 'filename of the postgresql dump in gcloud storage `gs://camunda-ops/optimize/`')
    stringParam('CLEANUP_TIMEOUT_MINUTES', '60', 'Time limit for a cleanup run to finish')
  }

  triggers {
    cron('H 5 * * *')
  }
}