/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

class OptimizeGeneratorWrapper {
  String generatorVersion
  String generatorDirectory
  String generatorArtifactId

  OptimizeGeneratorWrapper(String generatorVersion, String baseDirectory, String generatorArtifactId = "optimize-data-generator") {
    this.generatorVersion = generatorVersion
    this.generatorArtifactId = generatorArtifactId
    this.generatorDirectory = "${baseDirectory}/${generatorVersion}"
  }

  def generateOptimizeData() {
    println "Running Optimize Data Generator ${generatorVersion}..."
    def command = ["/bin/bash", "-c", "java -cp \"./lib/\" -jar ${generatorArtifactId}-${generatorVersion}.jar"]
    def generatorProcess = command.execute(null, new File(generatorDirectory))
    generatorProcess.consumeProcessOutput(System.out, System.err)
    generatorProcess.waitFor()
    if (generatorProcess.exitValue() == 0) {
      println "Successfully ran Optimize Data Generator ${generatorVersion}"
    } else {
      println "Error output: ${generatorProcess.text}"
      throw new Exception("Failed running Optimize Data Generator ${generatorVersion}!")
    }
  }

}
