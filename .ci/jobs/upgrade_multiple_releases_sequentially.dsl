pipelineJob('upgrade_multiple_releases_sequentially') {

  displayName 'Sequential Optimize Upgrade'
  description 'Upgrades sequentially over multiple Optimize releases and ensures reports still work.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/upgrade_multiple_releases_sequentially.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for performance tests.')
    stringParam('CAMBPM_VERSION', '7.10.6', 'Camunda BPM version to use.')
    stringParam('ES_VERSION', '6.2.0', 'Elasticsearch version to use.')
    stringParam('START_VERSION', '2.1.0', 'Version to start the sequential upgrade from')
    stringParam('UPGRADE_TIMEOUT', '1800', 'Timeout for the upgrade to complete')
  }

  triggers {
    cron('H 5 * * *')
  }
}