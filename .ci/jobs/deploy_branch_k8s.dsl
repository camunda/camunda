pipelineJob('deploy-optimize-branch-to-k8s') {

  displayName 'Deploy Optimize branch to K8s'
  description 'Deploys Optimize branch to Kubernetes.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/deploy_branch_k8s.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('INFRASTRUCTURE_BRANCH', 'SRE-155', 'Branch to use for checkout of deployment script.')
    stringParam('BRANCH', 'master', 'Branch to use for deployment.')
    booleanParam('DRY_RUN', false, 'Enable dry-run mode.')
  }

  jdk '(Default)'

  triggers {
    cron('H 3 * * *')
  }
}