pipelineJob('engine_compatibility') {

  displayName 'Integration Tests with supported CamBPM Versions'
  description 'Runs IT suite with different supported CamBPM versions to check compatibility.'

  // By default, this job is disabled in non-prod envs.
  if (binding.variables.get("ENVIRONMENT") != "prod") {
    disabled()
  }

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/engine_compatibility.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for ITs.')
  }

  properties {
    pipelineTriggers {
      triggers {
        cron {
          spec('H 22 * * *')
        }
      }
    }
  }
}
