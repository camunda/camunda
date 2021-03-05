/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package com.camunda.optimize.test.upgrade

import org.camunda.optimize.OptimizeRequestExecutor
import org.camunda.optimize.service.metadata.Version
import org.camunda.optimize.test.optimize.StatusClient

import javax.ws.rs.ProcessingException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

import static java.util.concurrent.TimeUnit.MINUTES
import static java.util.concurrent.TimeUnit.SECONDS
import static org.awaitility.Awaitility.await

class OptimizeWrapper {
  String optimizeVersion
  String optimizeDirectory
  String configDirectory
  Process process
  OptimizeRequestExecutor requestExecutor
  int elasticPort

  OptimizeWrapper(String optimizeVersion, String baseDirectory, int elasticPort = 9200, String configDirectory = "config") {
    this.optimizeVersion = optimizeVersion
    this.optimizeDirectory = "${baseDirectory}/${optimizeVersion}"
    this.configDirectory = "${optimizeDirectory}/${configDirectory}"
    this.requestExecutor = new OptimizeRequestExecutor("demo", "demo", "http://localhost:8090/api")
    this.elasticPort = elasticPort
  }

  def copyLicense(String licensePath) {
    Files.copy(
            new File(licensePath).toPath(),
            new File("${configDirectory}/OptimizeLicense.txt").toPath(),
            StandardCopyOption.REPLACE_EXISTING
    )
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
      return upgradeProcess
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
    environmentVars.add("OPTIMIZE_EVENT_BASED_PROCESSES_USER_IDS=[demo]")
    environmentVars.add("OPTIMIZE_EVENT_BASED_PROCESSES_IMPORT_ENABLED=true")
    environmentVars.add("OPTIMIZE_CAMUNDA_BPM_EVENT_IMPORT_ENABLED=true")
    environmentVars.add("OPTIMIZE_EVENT_INGESTION_ACCESS_TOKEN=secret")
    def command = ["/bin/bash", "./optimize-startup.sh"]
    this.process = command.execute(environmentVars, new File(optimizeDirectory))
    try {
      StatusClient statusClient = new StatusClient(() -> requestExecutor)
      println "Waiting for Optimize ${optimizeVersion} to boot..."
      def isOldVersion = isOldVersion()
      if (isOldVersion) {
        await()
        // this delay is here for avoiding race conditions of still running initializations
        // after the endpoint is available, should be solved with a proper health-check endpoint in future, OPT-3442
                .pollDelay(30, SECONDS)
                .atMost(timeoutInSeconds, MINUTES)
                .ignoreException(ProcessingException)
                .until(statusClient::getOldStatus, getStartPredicate(true))
      } else {
        await()
        // this delay is here for avoiding race conditions of still running initializations
        // after the endpoint is available, should be solved with a proper health-check endpoint in future, OPT-3442
                .pollDelay(30, SECONDS)
                .atMost(timeoutInSeconds, MINUTES)
                .ignoreException(ProcessingException)
                .until(statusClient::getStatus, getStartPredicate(false))
      }

      println "Optimize ${optimizeVersion} is up!"
      return this.process
    } catch (Exception e) {
      println "Optimize did not start within ${timeoutInSeconds}s."
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

  def waitForImportToFinish(int timeoutInMinutes = 90) {
    StatusClient statusClient = new StatusClient(() -> requestExecutor)
    println "Waiting for Optimize ${optimizeVersion} import to become idle..."
    def isOldVersion = isOldVersion()
    if (isOldVersion) {
      await()
              .atMost(timeoutInMinutes, MINUTES)
              .ignoreException(ProcessingException)
              .until(statusClient::getOldStatus, getStatusResponsePredicate(true))
    } else {
      await()
              .atMost(timeoutInMinutes, MINUTES)
              .ignoreException(ProcessingException)
              .until(statusClient::getStatus, getStatusResponsePredicate(false))
    }

    println "Optimize ${optimizeVersion} import is idle!"
  }

  private def isOldVersion() {
    def optimizeMajor = Version.getMajorVersionFrom(optimizeVersion)
    def optimizeMinor = Version.getMinorVersionFrom(optimizeVersion)
    return Integer.parseInt(optimizeMajor) < 3 || (Integer.parseInt(optimizeMajor) == 3 && Integer.parseInt(optimizeMinor) < 3)
  }

  private def getStartPredicate(boolean isOldVersion) {
    if (isOldVersion) {
      return (statusResponse -> statusResponse.connectionStatus.engineConnections.values().every())
    } else {
      return (statusResponse -> statusResponse.engineStatus.values().every(connectionStatus -> connectionStatus.getIsConnected()))
    }
  }

  private def getStatusResponsePredicate(boolean isOldVersion) {
    if (isOldVersion) {
      return (statusResponse -> statusResponse.isImporting.values().every(isImporting -> !isImporting))
    } else {
      return (statusResponse -> statusResponse.engineStatus.values().every(connectionStatus -> !connectionStatus.getIsImporting()))
    }
  }

  private static List<String> getCurrentEnvironmentVariables() {
    System.getenv().collect { key, value -> "$key=$value".toString() }
  }

}
