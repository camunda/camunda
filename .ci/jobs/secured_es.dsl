pipelineJob('secured_es') {

  displayName 'Connect to secured Elasticsearch'
  description 'Verify connection to a secured ES instance'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/secured_es.groovy'))
      sandbox()
    }
  }

    parameters {
      stringParam('BRANCH', 'master', 'Branch to use for the test.')
    }

  triggers {
    cron('H 5 * * *')
  }
}