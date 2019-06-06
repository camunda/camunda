pipelineJob('cluster_test') {

  displayName 'Optimize Cluster Test'
  description 'Run Optimize in a clustering setup.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/cluster_test.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for cluster tests.')
  }

  triggers {
    cron('H 5 * * *')
  }
}
