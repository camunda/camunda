/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.upgrade;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import io.camunda.optimize.OptimizeRequestExecutor;
import io.camunda.optimize.test.optimize.HealthClient;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.IOUtils;

public class OptimizeWrapper {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(OptimizeWrapper.class);
  private final String optimizeVersion;
  private final String optimizeDirectory;
  private Process process;
  private Process upgradeProcess;
  private final OptimizeRequestExecutor requestExecutor;
  private final int elasticPort;

  public OptimizeWrapper(
      final String optimizeVersion, final String baseDirectory, final int elasticPort) {
    this.optimizeVersion = optimizeVersion;
    optimizeDirectory = baseDirectory + "/" + optimizeVersion;
    requestExecutor = new OptimizeRequestExecutor("demo", "demo", "http://localhost:8090/api");
    this.elasticPort = elasticPort;
  }

  public void startUpgrade(final String outputFilePath) throws IOException {
    if (upgradeProcess != null) {
      throw new RuntimeException("Upgrade is already running, wait for it to finish.");
    }
    log.info(
        "Running upgrade to Optimize {} on Elasticsearch with port {}...",
        optimizeVersion,
        elasticPort);

    final ProcessBuilder processBuilder =
        new ProcessBuilder()
            .command("bash", "-c", "./upgrade/upgrade.sh --skip-warning")
            .directory(new File(optimizeDirectory))
            .redirectOutput(Redirect.to(new File(outputFilePath)));
    final Map<String, String> envVars = new HashMap<>();
    envVars.put("OPTIMIZE_ELASTICSEARCH_HTTP_PORT", String.valueOf(elasticPort));
    processBuilder.environment().putAll(envVars);
    upgradeProcess = processBuilder.start();
  }

  public void waitForUpgradeToFinish(final int timeoutInMinutes) throws Exception {
    if (upgradeProcess == null) {
      throw new RuntimeException("No Upgrade running. Start one first.");
    }
    upgradeProcess.waitFor(timeoutInMinutes, MINUTES);
    if (upgradeProcess.exitValue() == 0) {
      log.info("Successfully upgraded to Optimize {}", optimizeVersion);
      upgradeProcess = null;
    } else {
      log.info(
          "Error output: {}",
          IOUtils.readLines(upgradeProcess.getErrorStream(), Charset.defaultCharset()));
      throw new Exception("Failed upgrading to Optimize " + optimizeVersion);
    }
  }

  public synchronized void start(final String outputFilePath)
      throws IOException, InterruptedException {
    if (process != null) {
      throw new RuntimeException("Already started, stop it first.");
    }

    log.info("Starting Optimize {}...", optimizeVersion);

    final ProcessBuilder processBuilder =
        new ProcessBuilder()
            .command("bash", "-c", "./optimize-startup.sh")
            .redirectOutput(Redirect.to(new File(outputFilePath)))
            .directory(new File(optimizeDirectory));
    final Map<String, String> envVars = new HashMap<>();
    envVars.put("OPTIMIZE_ELASTICSEARCH_HTTP_PORT", String.valueOf(elasticPort));
    envVars.put("OPTIMIZE_API_ACCESS_TOKEN", "secret");
    envVars.put("SPRING_PROFILES_ACTIVE", "ccsm");
    envVars.put("CAMUNDA_OPTIMIZE_ELASTICSEARCH_SETTINGS_INDEX_NUMBER_OF_REPLICAS", "0");

    processBuilder.environment().putAll(envVars);
    process = processBuilder.start();

    try {
      final HealthClient healthClient = new HealthClient(() -> requestExecutor);
      log.info("Waiting for Optimize {} to boot...", optimizeVersion);
      await()
          // this delay is here for avoiding race conditions of still running initializations
          .pollDelay(20, SECONDS)
          .pollInterval(5, SECONDS)
          .atMost(60, SECONDS)
          .ignoreException(ProcessingException.class)
          .until(
              healthClient::getReadiness,
              response -> Response.Status.OK.getStatusCode() == response.getStatus());
      log.info("Optimize {} is up!", optimizeVersion);
    } catch (final Exception e) {
      log.error("Optimize did not start within 60s.");
      stop();
      throw e;
    }
  }

  public synchronized void stop() throws InterruptedException {
    if (process != null) {
      log.info("Stopping Optimize {}...", optimizeVersion);
      process.destroy();
      log.info("Optimize process exited with code: {}", process.waitFor());
      process = null;
      log.info("Optimize {} was stopped.", optimizeVersion);
    }
  }
}
