/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
pipelineJob('import-zeebe-data-performance') {

  displayName 'Import performance test on Zeebe dataset'
  description 'Test Optimize Import performance against a Zeebe dataset.'

  // By default, this job is disabled in non-prod envs.
  if (binding.variables.get("ENVIRONMENT") != "prod") {
    disabled()
  }

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/import_zeebe_data_performance.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', binding.variables.get('GIT_LOCAL_BRANCH', 'master'), 'Branch to use for performance tests.')

    stringParam('ZEEBE_PARTITION_COUNT', '3', 'Number of configured Zeebe partitions (must match data generation)')
    stringParam('DATA_INSTANCE_COUNT', '1000000', 'The overall number of instances in the data source (must match data generation)')
    stringParam('DATA_PROCESS_DEFINITION_COUNT', '100', 'The number of process definitions in the data source (must match data generation)')
    stringParam('ZEEBE_MAX_IMPORT_PAGE_SIZE', '10000', 'The max page size for importing Zeebe data')
    stringParam('IMPORT_TIMEOUT_IN_MINUTES', '240', 'The max. time in minutes the import performance tests are allowed to run')
    stringParam('ES_VERSION', '7.16.2', 'Elasticsearch version to use.')
    stringParam('ES_REFRESH_INTERVAL', '2s', 'Elasticsearch index refresh interval')
  }

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
