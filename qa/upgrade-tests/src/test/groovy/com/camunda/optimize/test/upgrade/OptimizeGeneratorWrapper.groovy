/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package com.camunda.optimize.test.upgrade

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class OptimizeGeneratorWrapper {
  private static final Logger log = LoggerFactory.getLogger(OptimizeWrapper.class);

  String generatorVersion
  String generatorDirectory
  String generatorArtifactId

  OptimizeGeneratorWrapper(String generatorVersion, String baseDirectory, String generatorArtifactId = "optimize-data-generator") {
    this.generatorVersion = generatorVersion
    this.generatorArtifactId = generatorArtifactId
    this.generatorDirectory = "${baseDirectory}/${generatorVersion}"
  }

  def generateOptimizeData() {
    log.info("Running Optimize Data Generator ${generatorVersion}...");
    def command = ["/bin/bash", "-c", "java -cp \"./lib/\" -jar ${generatorArtifactId}-${generatorVersion}.jar"]
    def generatorProcess = command.execute(null, new File(generatorDirectory))
    generatorProcess.consumeProcessOutput(System.out, System.err)
    generatorProcess.waitFor()
    if (generatorProcess.exitValue() == 0) {
      log.info("Successfully ran Optimize Data Generator ${generatorVersion}");
    } else {
      log.error("Error output: ${generatorProcess.text}");
      throw new Exception("Failed running Optimize Data Generator ${generatorVersion}!")
    }
  }

}
