pipelineJob('cluster_test') {

  displayName 'Optimize Cluster Test'
  description 'Run Optimize in a clustering setup.'

  // By default, this job is disabled in non-prod envs.
  if (binding.variables.get("ENVIRONMENT") != "prod") {
    disabled()
  }

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
