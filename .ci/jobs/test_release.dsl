pipelineJob('camunda-operate-release-test') {

  disabled(ENVIRONMENT != 'prod')
  displayName 'Test Camunda Operate Release Build'
  description 'Run Camunda Operate release without committing the changes to github or uploading the artifacts.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/release.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('RELEASE_VERSION', '1.0.0', 'Version to release. Applied to pom.xml and Git tag.')
    stringParam('DEVELOPMENT_VERSION', '1.1.0-SNAPSHOT', 'Next development version.')
    booleanParam('PUSH_CHANGES', false, 'DO NOT SET THIS TO TRUE! If you do, you will perform an actual release.')
  }

  properties {
    pipelineTriggers {
      triggers {
        // cron {
        //   spec('H 4 * * *')
        // }
      }
    }
  }
}
