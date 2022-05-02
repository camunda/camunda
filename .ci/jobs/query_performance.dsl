pipelineJob('query-performance-tests') {

  displayName 'Query performance test on static dataset'
  description 'Test Optimize Query Performance against a static dataset.'

  // By default, this job is disabled in non-prod envs.
  if (binding.variables.get("ENVIRONMENT") != "prod") {
    disabled()
  }

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/query_performance.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', binding.variables.get('GIT_LOCAL_BRANCH', 'master'), 'Branch to use for performance tests.')

    choiceParam('SQL_DUMP', ['optimize_data-query-performance.sqlc', 'optimize_data-medium.sqlc', 'optimize_data-large.sqlc', 'optimize_data-stage.sqlc'])
    stringParam('CAMBPM_VERSION', '', 'Camunda BPM version to use, defaults to reading it from pom.xml.')
    stringParam('ES_VERSION', '', 'Elasticsearch version to use, defaults to reading it from pom.xml.')
  }

  properties {
    pipelineTriggers {
      triggers {
        cron {
          spec('H 2 * * 1-5')
        }
      }
    }
  }
}
