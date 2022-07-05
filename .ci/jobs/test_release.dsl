pipelineJob('camunda-optimize-release-test') {

  displayName 'Test Camunda Optimize Release Build'
  description 'Run Camunda Optimize release without committing the changes to github or uploading the artifacts.'

  // By default, this job is disabled in non-prod envs.
  if (binding.variables.get("ENVIRONMENT") != "prod") {
    disabled()
  }

  definition {
    cpsScm {
      scriptPath('.ci/pipelines/release_optimize.groovy')
      lightweight(false)
      scm {
        git {
          remote {
            github 'camunda/camunda-optimize', 'https'
            credentials 'github-optimize-app'
          }
          branches('master')
        }
      }
    }
  }

  parameters {
    stringParam('RELEASE_VERSION', '0.0.0', 'Version to release. Applied to pom.xml and Git tag.')
    stringParam('DEVELOPMENT_VERSION', '0.1.0-SNAPSHOT', 'Next development version.')
    stringParam('BRANCH', binding.variables.get('GIT_LOCAL_BRANCH', 'master'), 'The branch used for the release checkout.')
    booleanParam('PUSH_CHANGES', false, 'DO NOT SET THIS TO TRUE! If you do, you will perform an actual release.')
    booleanParam('DOCKER_LATEST', false, 'Should the docker image be tagged as latest.')
    booleanParam('RELEASE_EXAMPLE', false, 'Should an example repository be released.')
  }
  
  properties {
    pipelineTriggers {
      triggers {
        cron {
          spec('H 23 * * 1-5')
        }
      }
    }
  }
}
