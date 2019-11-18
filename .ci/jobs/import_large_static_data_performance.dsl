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

    choiceParam('SQL_DUMP', ['optimize_large_data-performance.sqlc', 'optimize_large_data-stage.sqlc'])
    stringParam('EXPECTED_NUMBER_OF_PROCESS_INSTANCES', '10000000', '')
    stringParam('EXPECTED_NUMBER_OF_ACTIVITY_INSTANCES', '119091240', '')
    stringParam('EXPECTED_NUMBER_OF_USER_TASKS', '9422960', '')
    stringParam('EXPECTED_NUMBER_OF_VARIABLES', '110733240', '')
    stringParam('EXPECTED_NUMBER_OF_DECISION_INSTANCES', '3043474', '')
  }

  triggers {
    cron('H 1 * * *')
  }
}