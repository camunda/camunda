pipelineJob('deploy-optimize-zeebeint-to-k8s') {

  displayName 'Deploy Optimize Zeebe Integration instance to K8s'
  description 'Deploys Optimize Zeebe Integration instance to Kubernetes.'

  // By default, this job is disabled in non-prod envs.
  if (binding.variables.get("ENVIRONMENT") != "prod") {
    disabled()
  }

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/deploy_k8s_zeebeint.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('INFRASTRUCTURE_BRANCH', 'master', 'Branch to use for checkout of deployment script.')
    stringParam('BRANCH', 'prototype_zeebeint', 'Optimize branch and Docker image to use for deployment.')
    booleanParam('DRY_RUN', false, 'Enable dry-run mode.')
  }
}
