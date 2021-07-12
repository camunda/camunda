pipelineJob('deploy-optimize-persistent') {

  displayName 'Deploy Optimize persistent components'
  description 'Deploy Optimize persistent components to its namespace'

  // By default, this job is disabled in non-prod envs.
  if (binding.variables.get("ENVIRONMENT") != "prod") {
    disabled()
  }

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/deploy_optimize_persistent.groovy'))
      sandbox()
    }
  }
}
