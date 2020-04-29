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
