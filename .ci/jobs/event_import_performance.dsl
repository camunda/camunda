pipelineJob('event-import-performance') {

  displayName 'Optimize Event Based Process Import performance'
  description 'Test Optimize Event Based Process Import performance.'

  // By default, this job is disabled in non-prod envs.
  if (binding.variables.get("ENVIRONMENT") != "prod") {
    disabled()
  }

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/event_import_performance.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for performance tests.')

    choiceParam('SQL_DUMP', ['optimize_data-medium.sqlc', 'optimize_data-large.sqlc', 'optimize_data-stage.sqlc', 'optimize_data-e2e.sqlc'])
    stringParam('ES_REFRESH_INTERVAL', '2s', 'Elasticsearch index refresh interval.')
    stringParam('ES_NUM_NODES', '1', 'Number of Elasticsearch nodes in the cluster (not more than 5)')
    stringParam('EXTERNAL_EVENT_COUNT', '40000000', 'Number of external events to ingest.')
  }

  properties {
    pipelineTriggers {
      triggers {
        cron {
          spec('H 20 * * *')
        }
      }
    }
  }
}
