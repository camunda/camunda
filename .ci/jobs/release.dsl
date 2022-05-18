pipelineJob('zeebe-tasklist-release') {

  disabled(ENVIRONMENT != 'prod')
  displayName 'Zeebe Tasklist Release'
  description 'Release Zeebe Tasklist to Camunda Nexus and tag GitHub repository.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/release.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('RELEASE_VERSION', '1.0.0', 'Version to release. Applied to pom.xml and Git tag.')
    stringParam('DEVELOPMENT_VERSION', '1.1.0-SNAPSHOT', 'Next development version.')
    stringParam('BRANCH', 'master', 'Branch to build the release from.')
    booleanParam('PUSH_CHANGES', true, 'Should the changes be pushed to remote locations (Nexus).')
    booleanParam('GITHUB_UPLOAD_RELEASE', false, 'Should upload the release to github.')
    booleanParam('IS_LATEST', true, 'Should tag the docker image with "latest" tag.')
  }

}
