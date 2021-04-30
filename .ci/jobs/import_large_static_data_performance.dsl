pipelineJob('import-performance-large-static-dataset') {

  displayName 'Import performance test on large static dataset'
  description 'Test Optimize Import performance against a large static dataset.'

  // By default, this job is disabled in non-prod envs.
  if (binding.variables.get("ENVIRONMENT") != "prod") {
    disabled()
  }

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/import_large_static_data_performance.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for performance tests.')

    choiceParam('SQL_DUMP', ['optimize_data-large.sqlc', 'optimize_data-medium.sqlc', 'optimize_data-stage.sqlc', 'optimize_data-e2e.sqlc'])
    stringParam('ES_REFRESH_INTERVAL', '2s', 'Elasticsearch index refresh interval.')
    stringParam('ES_NUM_NODES', '1', 'Number of Elasticsearch nodes in the cluster (not more than 5)')
  }

  properties {
    pipelineTriggers {
      triggers {
        cron {
          spec('H 1 * * *')
        }
      }
    }
  }
}
