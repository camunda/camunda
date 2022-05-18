pipelineJob('import-performance-large-static-dataset') {

  disabled(ENVIRONMENT != 'prod')
  displayName 'Import performance test on large static dataset'
  description '''Test Operate Import performance against a large static dataset.
  <br>Following steps are being performed:
  <br>1. The Elasticsearch snapshot,
  containing set of data for test, is being downloaded from Google Cloud and restored in the new instance of Elasticsearch.
  <br>2. The test is run via Maven command
  <br>3. The Elasticsearch snapshot containing both Zeebe and Operate data is being created and uploaded to Google Cloud,
  it can be used later for Query performance test suite'''

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/import_large_static_data_performance.groovy'))
      sandbox()
    }
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
