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
    booleanParam('DRY_RUN', false, 'Enable dry-run mode.')
    stringParam('ES_VERSION', '', 'Elasticsearch version to use, defaults to reading it from pom.xml.')
    stringParam('CAMBPM_VERSION', '', 'Camunda BPM version to use, defaults to reading it from pom.xml.')
    stringParam('ZEEBE_VERSION', '', 'Identity version to use, defaults to reading it from pom.xml.')
    stringParam('IDENTITY_VERSION', '', 'Zeebe version to use, defaults to reading it from pom.xml.')
  }

  properties {
    pipelineTriggers {
      triggers {
        cron {
          spec('H 22 * * 1-5')
        }
      }
    }
  }
}
