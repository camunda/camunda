pipelineJob('cleanup-optimize-k8s-branches') {

  displayName 'Cleanup Optimize branch deployments on K8s'
  description 'Cleanup Optimize branch deployments on Kubernetes and corresponding docker images.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/cleanup_k8s_branches.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('INFRASTRUCTURE_BRANCH', 'master', 'Branch to use for checkout of deployment script.')
    booleanParam('DRY_RUN', false, 'Enable dry-run mode.')
  }

  triggers {
    cron('H 5 * * *')
  }
}
