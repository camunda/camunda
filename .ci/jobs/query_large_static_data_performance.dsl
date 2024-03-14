pipelineJob('query-performance-large-static-dataset') {

  disabled(ENVIRONMENT != 'prod')
  displayName 'Query performance test on large static dataset'
  description '''Test performance of Operate queries against a large static dataset.
  <br>Following steps are being performed:
  <br>1. The Elasticsearch snapshot,
  containing set of data for test, is being downloaded from Google Cloud and restored in the new instance of Elasticsearch.
  <br>2. The Operate instance is started
  <br>3. The test is run via Maven command'''

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/query_large_static_data_performance.groovy'))
      sandbox()
    }
  }

  properties {
    pipelineTriggers {
      triggers {
        cron {
          spec('H 3 * * *')
        }
      }
    }
  }
}
