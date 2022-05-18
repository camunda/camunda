pipelineJob('batch-operation-performance') {

  disabled(ENVIRONMENT != 'prod')
  displayName 'Batch operations performance test on large static dataset'
  description '''Test performance of batch operation execution against a large static dataset.
  <br>Following steps are being performed:
  <br>1. The Elasticsearch snapshot,
  containing set of data for test, is being downloaded from Google Cloud and restored in the new instance of Elasticsearch.
  <br>2. The test is run via Maven command'''

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/batch-operation-performance.groovy'))
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
