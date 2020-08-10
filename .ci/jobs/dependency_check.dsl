pipelineJob('dependency-check') {

  displayName 'Dependency check'
  description 'Inspect optimize for the new dependencies and notify if any.'

  // By default, this job is disabled in non-prod envs.
  if (binding.variables.get("ENVIRONMENT") != "prod") {
    disabled()
  }

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/dependency_check.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for dependency check')
  }

  properties {
    pipelineTriggers {
      triggers {
        cron {
          spec('H 4 * * *')
        }
      }
    }
  }
}
