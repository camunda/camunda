pipelineJob('deploy-optimize-branch-to-k8s') {

  displayName 'Deploy Optimize branch to K8s'
  description 'Deploys Optimize branch to Kubernetes.'

  // By default, this job is disabled in non-prod envs.
  if (binding.variables.get("ENVIRONMENT") != "prod") {
    disabled()
  }

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/deploy_k8s_branches.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('INFRASTRUCTURE_BRANCH', 'master', 'Branch to use for checkout of deployment script.')
    // We need to keep below parameter for scripts to work but since decisions
    // in https://jira.camunda.com/browse/INFRA-1235 and problems with
    // https://jira.camunda.com/browse/INFRA-1617 this only can have the
    // value `master` and should not be changed.
    stringParam('BRANCH', 'master', 'Optimize branch to use for deployment.')
    booleanParam('DRY_RUN', false, 'Enable dry-run mode.')
  }

  properties {
    pipelineTriggers {
      triggers {
        cron {
          spec('H 0 * * *')
        }
      }
    }
  }
}
