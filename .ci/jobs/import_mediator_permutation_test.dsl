pipelineJob('import_mediator_permutation_test') {

  displayName 'Import Mediator Permutation Test'
  description 'Run Import tests with random ordered import mediators'

  // By default, this job is disabled in non-prod envs.
  if (binding.variables.get("ENVIRONMENT") != "prod") {
    disabled()
  }

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/import_mediator_permutation_test.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for Import Mediator Permutation tests.')
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
