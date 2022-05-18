pipelineJob('create-elasticsearch-snapshots-generated-data') {

  disabled(ENVIRONMENT != 'prod')
  displayName 'Create elasticsearch snapshot with generated data'
  description 'Generate data and create snapshot to be used in other jobs (e.g. performance tests).'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/create_elasticsearch_snapshot_generated_data.groovy'))
      sandbox()
    }
  }

  //parameters {
  //  stringParam('BRANCH', 'master', 'Branch to use for performance tests.')
  //}
}
