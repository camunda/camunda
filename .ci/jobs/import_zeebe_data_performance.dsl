/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
pipelineJob('import-zeebe-data-performance') {

  displayName 'Import performance test on Zeebe dataset'
  description 'Test Optimize Import performance against a Zeebe dataset.'

  // Disable this job as this will be taken care of with our 2023 term goal to improve Optimize performance
  disabled()

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/import_zeebe_data_performance.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', binding.variables.get('GIT_LOCAL_BRANCH', 'master'), 'Branch to use for performance tests.')

    stringParam('ZEEBE_PARTITION_COUNT', '6', 'Number of configured Zeebe partitions (must match data generation)')
    stringParam('DATA_INSTANCE_COUNT', '10000000', 'The overall number of instances in the data source (must match data generation)')
    stringParam('DATA_PROCESS_DEFINITION_COUNT', '1000', 'The number of process definitions in the data source (must match data generation)')
    stringParam('ZEEBE_MAX_IMPORT_PAGE_SIZE', '10000', 'The max page size for importing Zeebe data')
    stringParam('IMPORT_TIMEOUT_IN_MINUTES', '4320', 'The max. time in minutes the import performance tests are allowed to run')
    stringParam('ES_VERSION', '7.16.2', 'Elasticsearch version to use.')
    stringParam('ES_REFRESH_INTERVAL', '2s', 'Elasticsearch index refresh interval')
    choiceParam('SNAPSHOT_FOLDER_NAME', ['zeebe-data-import-performance', 'zeebe-data-test'])
  }

  // Disable cron testing envs, in case someone tests the job and forgets to disable it
  if (ENVIRONMENT == "prod") {
    properties {
      pipelineTriggers {
        triggers {
          cron {
            spec('H 1 * * 1-5')
          }
        }
      }
    }
  }
}
