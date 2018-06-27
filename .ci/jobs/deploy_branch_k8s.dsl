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
    stringParam('INFRASTRUCTURE_BRANCH', 'master', 'Branch to use for checkout of deployment script.')
    stringParam('BRANCH', 'master', 'Branch to use for deployment.')
    stringParam('DOCKER_IMAGE', 'gcr.io/ci-30-162810/camunda-optimize', 'Docker image name without version.')
    booleanParam('DRY_RUN', false, 'Enable dry-run mode.')
  }

  jdk '(Default)'

  triggers {
    cron('H 3 * * *')
  }
}