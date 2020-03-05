pipelineJob('test_e2e') {

  displayName 'e2e'
  description '''e2e'''

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/test_e2e.groovy'))
      sandbox()
    }
  }
  
  triggers {
    //cron('H 5 * * *')
  }
}
