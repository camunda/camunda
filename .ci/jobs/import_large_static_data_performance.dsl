pipelineJob('import-performance-large-static-dataset') {

  displayName 'Import performance test on large static dataset'
  description 'Test Operate Import performance against a large static dataset.
  <br>Following steps are being performed:
  <br>1. The Elasticsearch snapshot,
  containing set of data for test, is being downloaded from Google Cloud and restored in the new instance of Elasticsearch.
  <br>2. The test is run via Maven command'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/import_large_static_data_performance.groovy'))
      sandbox()
    }
  }
  
  triggers {
    cron('H 1 * * *')
  }
}
