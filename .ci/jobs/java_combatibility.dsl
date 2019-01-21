pipelineJob('java_compatibility') {

  displayName 'Java compatibility test'
  description 'Run Optimize IT test suite with different Java versions.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/java_compatibility.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for Java compatibility tests.')
    stringParam('CAMBPM_VERSION', '7.10.0', 'Camunda BPM version to use.')
  }

  triggers {
    cron('H 0 * * *')
  }
}