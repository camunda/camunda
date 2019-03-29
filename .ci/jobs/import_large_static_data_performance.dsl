pipelineJob('import-performance-large-static-dataset') {

  displayName 'Import performance test on large static dataset'
  description 'Test Operate Import performance against a large static dataset.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/import_large_static_data_performance.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for performance tests.')
    //stringParam('SQL_DUMP', 'optimize_large_data_10M_procinst_wo_unneded_data_7.10.0_schema.sqlc', 'filename of the postgresql dump in gcloud storage `gs://camunda-ops/optimize/`')
  }

  // triggers {
  //   cron('H 1 * * *')
  // }
}
