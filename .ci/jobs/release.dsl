pipelineJob('camunda-optimize-release') {

  displayName 'Release Camunda Optimize'
  description 'Release Camunda Optimize to Camunda Nexus and tag GitHub repository.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/release.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('RELEASE_VERSION', '1.0.0', 'Version to release. Applied to pom.xml and Git tag.')
    stringParam('DEVELOPMENT_VERSION', '1.1.0-SNAPSHOT', 'Next development version.')
  }

  jdk '(Default)'

}
