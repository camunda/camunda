pipelineJob('sonarqube_analysis') {

  displayName 'Sonarqube Analysis'
  description 'Run sonarqube analysis once nightly.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/sonarqube_analysis.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for Sonarqube analysis.')
    stringParam('ES_VERSION', '', 'Elasticsearch version to use, defaults to reading it from pom.xml.')
    stringParam('CAMBPM_VERSION', '', 'Camunda BPM version to use, defaults to reading it from pom.xml.')
  }

  properties {
    pipelineTriggers {
      triggers {
        cron {
          spec('H 0 * * *')
        }
      }
    }
  }
}
