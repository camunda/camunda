pipelineJob('cleanup-operate-k8s-branches') {

  disabled(ENVIRONMENT != 'prod')
  displayName 'Cleanup Operate branch deployments on K8s'
  description 'Cleanup Operate branch deployments on Kubernetes and corresponding docker images.'

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
