pipelineJob('secured_es') {

  displayName 'Connect to secured Elasticsearch'
  description 'Verify connection to a secured ES instance'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/secured_es.groovy'))
      sandbox()
    }
  }

  triggers {
    cron('H 5 * * *')
  }
}