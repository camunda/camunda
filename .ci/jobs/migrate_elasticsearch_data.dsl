pipelineJob('migrate-elasticsearch-data') {

  disabled(ENVIRONMENT != 'prod')
  displayName 'Migrates elasticsearch data from one to another version '
  description '''Migrates elasticsearch data from one to another version.
  <br>Following steps are being performed:
  <br>1. Start (previous) operate environment: elasticsearch, zeebe and operate (K8s, Docker)
  <br>2. Generate testdata (maven)
  <br>3. Run migration (shell scripts)
  <br>4. Check migrated data (maven)
  '''

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/migrate-elasticsearch-data.groovy'))
      sandbox()
    }
  }

  properties {
    pipelineTriggers {
      triggers {
        cron {
          spec('H 5 * * *')
        }
      }
    }
  }
}
