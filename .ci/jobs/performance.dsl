pipelineJob('Performance') {
  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/performance.groovy'))
      sandbox()
    }
  }
}