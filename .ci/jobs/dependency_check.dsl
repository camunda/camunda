pipelineJob('dependency-check') {

  displayName 'Dependency check'
  description 'Inspect optimize for the new dependencies and notify if any.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/dependency_check.groovy'))
      sandbox()
    }
  }

  triggers {
    cron('H 4 * * *')
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for dependency check')
  }

}