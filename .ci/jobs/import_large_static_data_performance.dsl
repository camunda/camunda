pipelineJob('import-performance-large-static-dataset') {

  displayName 'Import performance test on large static dataset'
  description 'Test Operate Import performance against a large static dataset.'

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
