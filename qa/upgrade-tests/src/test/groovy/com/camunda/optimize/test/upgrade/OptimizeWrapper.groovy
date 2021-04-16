/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package com.camunda.optimize.test.upgrade

import org.camunda.optimize.OptimizeRequestExecutor
import org.camunda.optimize.service.metadata.Version
import org.camunda.optimize.test.optimize.StatusClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.ws.rs.ProcessingException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

import static java.util.concurrent.TimeUnit.MINUTES
import static java.util.concurrent.TimeUnit.SECONDS
import static org.awaitility.Awaitility.await

class OptimizeWrapper {
  private static final Logger log = LoggerFactory.getLogger(OptimizeWrapper.class);

  String optimizeVersion
  String optimizeDirectory
  String configDirectory
  Process process
  Process upgradeProcess
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

  def startUpgrade() {
    if (this.upgradeProcess) {
      throw new RuntimeException("Upgrade is already running, wait for it to finish.")
    }
    log.info("Running upgrade to Optimize ${optimizeVersion} on Elasticsearch with port ${elasticPort}...");
    def environmentVars = getCurrentEnvironmentVariables()
    environmentVars.add("OPTIMIZE_ELASTICSEARCH_HTTP_PORT=${elasticPort}")
    def command = ["/bin/bash", "./upgrade/upgrade.sh", "--skip-warning"]
    this.upgradeProcess = command.execute(environmentVars, new File(optimizeDirectory))
    return this.upgradeProcess
  }

  def waitForUpgradeToFinish(int timeoutInMinutes = 90) {
    if (this.upgradeProcess == null) {
      throw new RuntimeException("No Upgrade running start one first.")
    }
    this.upgradeProcess.waitFor(timeoutInMinutes, MINUTES)
    if (this.upgradeProcess.exitValue() == 0) {
      log.info("Successfully upgraded to Optimize ${optimizeVersion}");
      this.upgradeProcess = null;
    } else {
      log.info("Error output: ${upgradeProcess.text}");
      throw new Exception("Failed upgrading to Optimize ${optimizeVersion}!")
    }
  }

  synchronized def start(int timeoutInSeconds = 60) {
    if (this.process) {
      throw new RuntimeException("Already started, stop it first.")
    }
    log.info("Starting Optimize ${optimizeVersion}...");
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
      log.info("Waiting for Optimize ${optimizeVersion} to boot...");
      def isOldVersion = isOldVersion()
      if (isOldVersion) {
        await()
        // this delay is here for avoiding race conditions of still running initializations
        // after the endpoint is available, should be solved with a proper health-check endpoint in future, OPT-3442
                .pollDelay(30, SECONDS)
                .atMost(timeoutInSeconds, SECONDS)
                .ignoreException(ProcessingException)
                .until(statusClient::getOldStatus, getStartPredicate(true))
      } else {
        await()
        // this delay is here for avoiding race conditions of still running initializations
        // after the endpoint is available, should be solved with a proper health-check endpoint in future, OPT-3442
                .pollDelay(30, SECONDS)
                .atMost(timeoutInSeconds, SECONDS)
                .ignoreException(ProcessingException)
                .until(statusClient::getStatus, getStartPredicate(false))
      }

      log.info("Optimize ${optimizeVersion} is up!");
      return this.process
    } catch (Exception e) {
      log.error("Optimize did not start within ${timeoutInSeconds}s.");
      stop()
      throw e
    }
  }

  synchronized def stop() {
    if (this.process) {
      log.info("Stopping Optimize ${optimizeVersion}...");
      this.process.destroy()
      this.process = null
      log.info("Optimize ${optimizeVersion} was stopped.");
    }
  }

  def waitForImportToFinish(int timeoutInMinutes = 90) {
    StatusClient statusClient = new StatusClient(() -> requestExecutor)
    log.info("Waiting for Optimize ${optimizeVersion} import to become idle...");
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

    log.info("Optimize ${optimizeVersion} import is idle!");
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
