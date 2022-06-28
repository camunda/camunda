/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package com.camunda.optimize.test.upgrade

import org.camunda.optimize.OptimizeRequestExecutor
import org.camunda.optimize.test.optimize.HealthClient
import org.camunda.optimize.test.optimize.StatusClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.ws.rs.ProcessingException
import javax.ws.rs.core.Response
import java.nio.file.Files
import java.nio.file.StandardCopyOption

import static java.util.concurrent.TimeUnit.MINUTES
import static java.util.concurrent.TimeUnit.SECONDS
import static org.awaitility.Awaitility.await

class OptimizeWrapper {
  private static final Logger log = LoggerFactory.getLogger(OptimizeWrapper.class);

  String optimizeVersion
  String optimizeDirectory
  Process process
  Process upgradeProcess
  OptimizeRequestExecutor requestExecutor
  int elasticPort

  OptimizeWrapper(String optimizeVersion, String baseDirectory, int elasticPort = 9200) {
    this.optimizeVersion = optimizeVersion
    this.optimizeDirectory = "${baseDirectory}/${optimizeVersion}"
    this.requestExecutor = new OptimizeRequestExecutor("demo", "demo", "http://localhost:8090/api")
    this.elasticPort = elasticPort
  }

  def copyLicense(String licensePath) {
    Files.copy(
      new File(licensePath).toPath(),
      new File("${optimizeDirectory}/config/OptimizeLicense.txt").toPath(),
      StandardCopyOption.REPLACE_EXISTING
    )
  }

  def startUpgrade(FileWriter upgradeOutputWriter) {
    if (this.upgradeProcess) {
      throw new RuntimeException("Upgrade is already running, wait for it to finish.")
    }
    log.info("Running upgrade to Optimize ${optimizeVersion} on Elasticsearch with port ${elasticPort}...");
    def environmentVars = getCurrentEnvironmentVariables()
    environmentVars.add("OPTIMIZE_ELASTICSEARCH_HTTP_PORT=${elasticPort}")
    def command = ["/bin/bash", "./upgrade/upgrade.sh", "--skip-warning"]
    this.upgradeProcess = command.execute(environmentVars, new File(optimizeDirectory))
    this.upgradeProcess.consumeProcessOutputStream(upgradeOutputWriter)
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

  synchronized def start(FileWriter outPutStreamWriter) {
    if (this.process) {
      throw new RuntimeException("Already started, stop it first.")
    }
    log.info("Starting Optimize ${optimizeVersion}...");
    def environmentVars = getCurrentEnvironmentVariables()
    environmentVars.add("OPTIMIZE_ELASTICSEARCH_HTTP_PORT=${elasticPort}")
    environmentVars.add("OPTIMIZE_EVENT_BASED_PROCESSES_USER_IDS=[demo]")
    environmentVars.add("OPTIMIZE_EVENT_BASED_PROCESSES_IMPORT_ENABLED=true")
    environmentVars.add("OPTIMIZE_CAMUNDA_BPM_EVENT_IMPORT_ENABLED=true")
    environmentVars.add("CAMUNDA_OPTIMIZE_DATA_ARCHIVE_ENABLED=false")
    environmentVars.add("OPTIMIZE_API_ACCESS_TOKEN=secret")
    def command = ["/bin/bash", "./optimize-startup.sh"]

    this.process = command.execute(environmentVars, new File(optimizeDirectory))
    this.process.consumeProcessOutputStream(outPutStreamWriter)

    try {
      HealthClient healthClient = new HealthClient(() -> requestExecutor)
      log.info("Waiting for Optimize ${optimizeVersion} to boot...");
      await()
      // this delay is here for avoiding race conditions of still running initializations
        .pollDelay(30, SECONDS)
        .atMost(60, SECONDS)
        .ignoreException(ProcessingException)
        .until(
          healthClient::getReadiness,
          response -> Response.Status.OK.getStatusCode() == response.getStatus()
        )
      log.info("Optimize ${optimizeVersion} is up!");
      return this.process
    } catch (Exception e) {
      log.error("Optimize did not start within 60s.");
      stop()
      throw e
    }
  }

  synchronized def stop() {
    if (this.process) {
      log.info("Stopping Optimize ${optimizeVersion}...");
      this.process.destroy()
      log.info("Optimize process exited with code: ${this.process.waitFor()}");
      this.process = null
      log.info("Optimize ${optimizeVersion} was stopped.");
    }
  }

  def waitForImportToFinish(int timeoutInMinutes = 90) {
    StatusClient statusClient = new StatusClient(() -> requestExecutor)
    log.info("Waiting for Optimize ${optimizeVersion} import to become idle...");
    await()
      .atMost(timeoutInMinutes, MINUTES)
      .ignoreException(ProcessingException)
      .until(
        statusClient::getStatus,
        statusResponse -> statusResponse.engineStatus.values().every(connectionStatus -> !connectionStatus.getIsImporting())
      )
    log.info("Optimize ${optimizeVersion} import is idle!");
  }

  private static List<String> getCurrentEnvironmentVariables() {
    System.getenv().collect { key, value -> "$key=$value".toString() }
  }

}
