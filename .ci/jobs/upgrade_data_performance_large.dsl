pipelineJob('upgrade-performance-static-dataset') {

  displayName 'Upgrade performance test on static dataset'
  description 'Test Optimize upgrade performance against a static dataset.'

  // By default, this job is disabled in non-prod envs.
  if (binding.variables.get("ENVIRONMENT") != "prod") {
    disabled()
  }

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/upgrade_data_performance.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for performance tests.')

    choiceParam('SQL_DUMP', ['optimize_data-medium.sqlc', 'optimize_data-large.sqlc', 'optimize_data-stage.sqlc'])
    stringParam('CAMBPM_VERSION', '', 'Camunda BPM version to use, defaults to reading it from pom.xml.')
    stringParam('ES_VERSION', '', 'Elasticsearch version to use, defaults to reading it from pom.xml.')
    stringParam('PREV_ES_VERSION', '', 'Previous Elasticsearch version that was used in the old Optimize prior to the migration, defaults to reading it from pom.xml.')
    stringParam('UPGRADE_TIMEOUT_MINUTES', '180', 'Timeout for the upgrade stage to complete in minutes.')
  }

  properties {
    pipelineTriggers {
      triggers {
        cron {
          spec('H 4 * * *')
        }
      }
    }
  }
}
