pipelineJob('elasticsearch_compatibility') {

  displayName 'Elasticsearch compatibility test'
  description 'Run Optimize IT test suite against different elasticsearch versions.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/elasticsearch_compatibility.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for elasticsearch compatibility tests.')
    stringParam('CAMBPM_VERSION', '', 'Camunda BPM version to use, defaults to reading it from pom.xml.')
  }

  triggers {
    cron('H 23 * * *')
  }
}