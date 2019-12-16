pipelineJob('camunda-optimize-release-test') {

  displayName 'Test Camunda Optimize Release Build'
  description 'Run Camunda Optimize release without committing the changes to github or uploading the artifacts.'

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
    stringParam('RELEASE_VERSION', '3.0.0', 'Version to release. Applied to pom.xml and Git tag.')
    stringParam('DEVELOPMENT_VERSION', '3.1.0-SNAPSHOT', 'Next development version.')
    stringParam('BRANCH', 'master', 'The branch used for the release checkout.')
    booleanParam('PUSH_CHANGES', false, 'DO NOT SET THIS TO TRUE! If you do, you will perform an actual release.')
    booleanParam('RELEASE_EXAMPLE', false, 'Should an example repository be released.')
  }
  
  triggers {
    cron('H 4 * * *')
  }
  
}
