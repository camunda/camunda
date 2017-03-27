pipelineJob('Performance') {
  definition {
    cps {
      script(readFileFromWorkspace('performance.groovy'))
      sandbox()
    }
  }
}