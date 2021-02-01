pipelineJob('cleanup-optimize-k8s-branches') {

  displayName 'Cleanup Optimize branch deployments on K8s'
  description 'Cleanup Optimize branch deployments on Kubernetes and corresponding docker images.'

  // By default, this job is disabled in non-prod envs.
  if (binding.variables.get("ENVIRONMENT") != "prod") {
    disabled()
  }

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
