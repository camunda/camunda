pipelineJob('import-performance-large-static-dataset') {

  displayName 'Import performance test on large static dataset'
  description 'Test Optimize Import performance against a large static dataset.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/import_large_static_data_performance.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for performance tests.')
    stringParam('SQL_DUMP', 'optimize_large_data_10M_procinst_wo_unneded_data_7.10.0_schema.sqlc', 'filename of the postgresql dump in gcloud storage `gs://camunda-ops/optimize/`')
    stringParam('EXPECTED_NUMBER_OF_PROCESS_INSTANCES', '10000007', '')
    stringParam('EXPECTED_NUMBER_OF_ACTIVITY_INSTANCES', '123162903', '')
    stringParam('EXPECTED_NUMBER_OF_USER_TASKS', '1611181', '')
    stringParam('EXPECTED_NUMBER_OF_VARIABLES', '108349190', '')
    stringParam('EXPECTED_NUMBER_OF_DECISION_INSTANCES', '2500006', '')
  }

  triggers {
    cron('H 1 * * *')
  }
}