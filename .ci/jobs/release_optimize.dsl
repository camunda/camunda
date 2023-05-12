pipelineJob('camunda-optimize-release') {

  displayName 'Release Camunda Optimize'
  description 'Release Camunda Optimize to Camunda Nexus and tag GitHub repository.'

  // By default, this job is disabled in non-prod envs.
  if (ENVIRONMENT != "prod") {
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
    booleanParam('PUSH_CHANGES', true, 'Should the changes be pushed to remote locations.')
    booleanParam('DOCKER_LATEST', true, 'Should the docker image be tagged as latest.')
    stringParam('ADDITIONAL_DOCKER_TAG', '', '(Optional) Any additional tag that should be added to the docker image.')
    booleanParam('RELEASE_EXAMPLE', true, 'Should an example repository be released.')
  }

}
