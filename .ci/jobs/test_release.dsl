pipelineJob('camunda-optimize-release-test') {

  displayName 'Test Camunda Optimize Release Build'
  description 'Run Camunda Optimize release without committing the changes to github or uploading the artefacts.'

  definition {
    cpsScm {
      scriptPath('.ci/pipelines/release.groovy')
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
    booleanParam('PUSH_CHANGES', false, 'Should the changes be pushed to remote locations.')
  }
  
  triggers {
    cron('H 4 * * *')
  }

}
