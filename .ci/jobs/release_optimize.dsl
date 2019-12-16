pipelineJob('camunda-optimize-release') {

  displayName 'Release Camunda Optimize'
  description 'Release Camunda Optimize to Camunda Nexus and tag GitHub repository.'

  definition {
    cpsScm {
      scriptPath('.ci/pipelines/release_optimize.groovy')
      lightweight(false)
      scm {
        git {
          remote {
            github('camunda/camunda-optimize')
            credentials('camunda-jenkins-github')
          }
          branches('master')
        }
      }
    }
  }

  parameters {
    stringParam('RELEASE_VERSION', '2.3.0', 'Version to release. Applied to pom.xml and Git tag.')
    stringParam('DEVELOPMENT_VERSION', '2.4.0-SNAPSHOT', 'Next development version.')
    stringParam('BRANCH', 'master', 'The branch used for the release checkout.')
    booleanParam('PUSH_CHANGES', true, 'Should the changes be pushed to remote locations.')
    booleanParam('RELEASE_EXAMPLE', true, 'Should an example repository be released.')
  }

}
