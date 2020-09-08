/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import org.camunda.optimize.OptimizeRequestExecutor
import org.camunda.optimize.test.optimize.StatusClient

import javax.ws.rs.ProcessingException

import static java.util.concurrent.TimeUnit.MINUTES
import static java.util.concurrent.TimeUnit.SECONDS
import static org.awaitility.Awaitility.await

class OptimizeWrapper {
  String optimizeVersion
  String optimizeDirectory
  Process process
  OptimizeRequestExecutor requestExecutor
  int elasticPort

  OptimizeWrapper(String optimizeVersion, String baseDirectory, int elasticPort = 9200) {
    this.optimizeVersion = optimizeVersion
    this.optimizeDirectory = "${baseDirectory}/${optimizeVersion}"
    this.requestExecutor = new OptimizeRequestExecutor("demo", "demo", "http://localhost:8090/api")
    this.elasticPort = elasticPort
  }

  def runUpgrade() {
    println "Running upgrade to Optimize ${optimizeVersion} on Elasticsearch with port ${elasticPort}..."
    def environmentVars = getCurrentEnvironmentVariables()
    environmentVars.add("OPTIMIZE_ELASTICSEARCH_HTTP_PORT=${elasticPort}")
    def command = ["/bin/bash", "./upgrade/upgrade.sh", "--skip-warning"]
    def upgradeProcess = command.execute(environmentVars, new File(optimizeDirectory))
    upgradeProcess.waitFor()
    if (upgradeProcess.exitValue() == 0) {
      println "Successfully upgraded to Optimize ${optimizeVersion}"
    } else {
      println "Error output: ${upgradeProcess.text}"
      throw new Exception("Failed upgrading to Optimize ${optimizeVersion}!")
    }
  }

  synchronized def start(int timeoutInSeconds = 60) {
    if (this.process) {
      throw new RuntimeException("Already started, stop it first.")
    }
    println "Starting Optimize ${optimizeVersion}..."
    def environmentVars = getCurrentEnvironmentVariables()
    environmentVars.add("OPTIMIZE_ELASTICSEARCH_HTTP_PORT=${elasticPort}")
    def command = ["/bin/bash", "./optimize-startup.sh"]
    this.process = command.execute(environmentVars, new File(optimizeDirectory))
    try {
      StatusClient statusClient = new StatusClient(() -> requestExecutor)
      println "Waiting for Optimize ${optimizeVersion} to boot..."
      await()
        .atMost(timeoutInSeconds, SECONDS)
        .ignoreException(ProcessingException)
        .until(
          statusClient::getStatus,
          statusResponse -> statusResponse.connectionStatus.engineConnections.values().every()
        )
      // this sleep is here for avoiding race conditions if still running initializations
      // after the endpoint is available
      sleep(3000)
      println "Optimize ${optimizeVersion} is up!"
    } catch (Exception e) {
      println "Optimize did not start within ${timeoutInSeconds}s."
      println this.process.text
      stop()
      throw e
    }
  }

  synchronized def stop() {
    if (this.process) {
      println "Stopping Optimize ${optimizeVersion}..."
      this.process.destroy()
      this.process = null
      println "Optimize ${optimizeVersion} was stopped."
    }
  }

  def waitForImportToFinish(int timeoutInMinutes = 60) {
    StatusClient statusClient = new StatusClient(() -> requestExecutor)
    println "Waiting for Optimize ${optimizeVersion} import to become idle..."
    await()
      .atMost(timeoutInMinutes, MINUTES)
      .ignoreException(ProcessingException)
      .until(
        statusClient::getStatus,
        statusResponse -> statusResponse.isImporting.values().every(isImporting -> !isImporting)
      )
    println "Optimize ${optimizeVersion} import is idle!"
  }

  private static List<GString> getCurrentEnvironmentVariables() {
    System.getenv().collect { key, value -> "$key=$value" }
  }

}
