pipelineJob('engine_compatibility') {

  displayName 'Integration Tests with supported CamBPM Versions'
  description 'Runs IT suite with different supported CamBPM versions to check compatibility.'

  // By default, this job is disabled in non-prod envs.
  if (ENVIRONMENT != "prod") {
    disabled()
  }

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/engine_compatibility.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', binding.variables.get('GIT_LOCAL_BRANCH', 'master'), 'Branch to use for ITs.')
  }

  // Disable cron testing envs, in case someone tests the job and forgets to disable it
  if (ENVIRONMENT == "prod") {
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
}
