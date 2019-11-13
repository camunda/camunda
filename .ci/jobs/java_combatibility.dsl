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
    stringParam('ES_VERSION', '', 'Elasticsearch version to use, defaults to reading it from pom.xml.')
    stringParam('CAMBPM_VERSION', '', 'Camunda BPM version to use, defaults to reading it from pom.xml.')
  }

  triggers {
    cron('H 0 * * *')
  }
}