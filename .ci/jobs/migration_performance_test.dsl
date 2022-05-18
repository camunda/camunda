pipelineJob('migration-performance-test') {

  disabled(ENVIRONMENT != 'prod')
  displayName 'Test migration performance on large dataset'
  description '''Test performance of Operate migration against a large dataset.
  <br>Following steps are being performed:
  <br>1. The Elasticsearch snapshot,
  containing set of data for test, is being downloaded from Google Cloud and restored in the new instance of Elasticsearch.
  <br>2. The Operate Migration instance is started
  <br>3. The test is run via Maven command'''

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/migration_performance_test.groovy'))
      sandbox()
    }
  }

  properties {
    pipelineTriggers {
      triggers {
        cron {
          spec('H 5 * * *')
        }
      }
    }
  }
}
