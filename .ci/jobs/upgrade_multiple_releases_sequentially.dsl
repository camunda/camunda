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
    stringParam('CAMBPM_VERSION', '', 'Camunda BPM version to use, defaults to reading it from pom.xml.')
    stringParam('ES_VERSION', '', 'Elasticsearch version to use, defaults to reading it from pom.xml.')
    stringParam('START_VERSION', '2.1.0', 'Version to start the sequential upgrade from')
    stringParam('UPGRADE_TIMEOUT', '1800', 'Timeout for the upgrade to complete')
  }

  triggers {
    cron('H 5 * * *')
  }
}