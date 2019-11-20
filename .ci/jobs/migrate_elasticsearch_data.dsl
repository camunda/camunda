pipelineJob('migrate-elasticsearch-data') {

  displayName 'Migrates elasticsearch data from one to another version '
  description '''Migrates elasticsearch data from one to another version.
  <br>Following steps are being performed:
  <br>1. Start (previous) operate environment: elasticsearch, zeebe and operate 
  <br>2. Generate testdata with zeebe client
  <br>3. Stop (previous) operate
  <br>4. Run migration shell scripts
  <br>5. Start (next) operate
  <br>6. Check migrated data
  '''

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/migrate-elasticsearch-data.groovy'))
      sandbox()
    }
  }
  
  //triggers {
  //  cron('H 3 * * *')
  //}
}
