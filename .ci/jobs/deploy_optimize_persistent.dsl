pipelineJob('deploy-optimize-persistent') {

  displayName 'Deploy Optimize persistent components'
  description 'Deploy Optimize persistent components to its namespace'

  // By default, this job is disabled in non-prod envs.
  if (ENVIRONMENT != "prod") {
    disabled()
  }

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/deploy_optimize_persistent.groovy'))
      sandbox()
    }
  }

  parameters {
      stringParam('OPTIMIZE_VERSION', 'latest', 'Optimize Docker tag to deploy.')
      stringParam('ES_VERSION', 'latest', 'ES Docker tag to deploy.')
      stringParam('CAMBPM_VERSION', 'latest', 'CAMBPM Docker tag to deploy.')
    }
}
