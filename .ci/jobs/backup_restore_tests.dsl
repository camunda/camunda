pipelineJob('backup-restore-tests') {

  disabled(ENVIRONMENT != 'prod')
  displayName 'Backup and restore test'
  description '''Test backup and restore of Operate data'''

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/backup_restore_tests.groovy'))
      sandbox()
    }
  }

  properties {
    pipelineTriggers {
      triggers {
        cron {
          spec('H 3 * * *')
        }
      }
    }
  }
}
