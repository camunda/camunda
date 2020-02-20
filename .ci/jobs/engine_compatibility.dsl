pipelineJob('engine_compatibility') {

  displayName 'Integration Tests against previous CamBPM Versions'
  description 'Runs integration tests against the previous two CamBPM versions to check compatibility.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/engine_compatibility.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for Engine compatibility tests.')
  }

  triggers {
    cron('H 22 * * *')
  }
}