pipelineJob('event-import-performance') {

  displayName 'Optimize Event Based Process Import performance'
  description 'Test Optimize Event Based Process Import performance.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/event_import_performance.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for performance tests.')

    choiceParam('SQL_DUMP', ['optimize_large_data-performance.sqlc', 'optimize_large_data-stage.sqlc'])
    stringParam('ES_REFRESH_INTERVAL', '2s', 'Elasticsearch index refresh interval.')
    stringParam('EXTERNAL_EVENT_COUNT', '40000000', 'Number of external events to ingest.')
  }

  triggers {
    cron('H 20 * * *')
  }
}