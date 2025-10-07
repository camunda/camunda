/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.upgrade.wrapper;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.ELASTICSEARCH_DATABASE_PROPERTY;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.OPENSEARCH_DATABASE_PROPERTY;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import io.camunda.optimize.OptimizeRequestExecutor;
import io.camunda.optimize.service.util.configuration.DatabaseType;
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
import org.jetbrains.annotations.NotNull;

public class OptimizeWrapper {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(OptimizeWrapper.class);
  private final String optimizeVersion;
  private final String optimizeDirectory;
  private final DatabaseType databaseType;
  private Process process;
  private Process upgradeProcess;
  private final OptimizeRequestExecutor requestExecutor;
  private final int databasePort;

  public OptimizeWrapper(
      final DatabaseType databaseType,
      final String optimizeVersion,
      final String baseDirectory,
      final int databasePort) {
    this.optimizeVersion = optimizeVersion;
    this.databaseType = databaseType;
    optimizeDirectory = baseDirectory + "/" + optimizeVersion;
    requestExecutor = new OptimizeRequestExecutor("demo", "demo", "http://localhost:8090/api");
    this.databasePort = databasePort;
  }

  public void startUpgrade(final String outputFilePath) throws IOException {
    if (upgradeProcess != null) {
      throw new RuntimeException("Upgrade is already running, wait for it to finish.");
    }
    log.info(
        "Running upgrade to Optimize {} on {} with port {}...",
        optimizeVersion,
        databaseType.getId(),
        databasePort);

    final ProcessBuilder processBuilder =
        new ProcessBuilder()
            .command("bash", "-c", "./upgrade/upgrade.sh --skip-warning")
            .directory(new File(optimizeDirectory))
            .redirectOutput(Redirect.to(new File(outputFilePath)));
    final Map<String, String> envVars = new HashMap<>();
    envVars.put("OPTIMIZE_ELASTICSEARCH_HTTP_PORT", String.valueOf(databasePort));
    envVars.put("CAMUNDA_OPTIMIZE_OPENSEARCH_HTTP_PORT", String.valueOf(databasePort));
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
    log.info("Optimize will be started with command: bash -c ./optimize-startup.sh in directory: {}", optimizeDirectory);
    final ProcessBuilder processBuilder =
        new ProcessBuilder()
            .command("bash", "-c", "./optimize-startup.sh")
            .redirectOutput(Redirect.to(new File(outputFilePath)))
            .directory(new File(optimizeDirectory));
    final Map<String, String> envVars = getEnvVarsMap();
    log.info("Environment variables: {}", envVars);
    processBuilder.environment().putAll(envVars);
    try {
      process = processBuilder.start();
    } catch (IOException ioe) {
      log.error("Failed to start Optimize process: {}", ioe.getMessage(), ioe);
      throw ioe;
    }

    boolean startedSuccessfully = false;
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
      startedSuccessfully = true;
    } catch (final Exception e) {
      log.error("Optimize did not start within 60s.");
      if (process != null && !process.isAlive()) {
        try {
          int exitCode = process.exitValue();
          log.error("Optimize process exited prematurely with code: {}", exitCode);
        } catch (IllegalThreadStateException itse) {
          log.error("Process is not alive but exit code not available.");
        }
      } else if (process != null) {
        log.error("Optimize process is still running but not responding to health checks");
      }
      // Log last 50 lines of output file
      try {
        File outFile = new File(outputFilePath);
        if (outFile.exists()) {
          java.util.List<String> lines = java.nio.file.Files.readAllLines(outFile.toPath());
          log.error("Complete startup log ({} lines):", lines.size());
          for (int i = 0; i < lines.size(); i++) {
            log.error("[OPTIMIZE START]  {}: {}", i + 1, lines.get(i));
          }
        } else {
          log.error("Startup output file {} does not exist.", outputFilePath);
        }
      } catch (Exception logEx) {
        log.error("Failed to read startup output file: {}", logEx.getMessage(), logEx);
      }
      stop();
      throw e;
    } finally {
      // Always print the output file to the logger for CI visibility
      try {
        File outFile = new File(outputFilePath);
        if (outFile.exists()) {
          java.util.List<String> lines = java.nio.file.Files.readAllLines(outFile.toPath());
          int from = Math.max(0, lines.size() - 50);
          if (startedSuccessfully) {
            log.info("Last {} lines of startup log (on success):\n{}", lines.size() - from, String.join("\n", lines.subList(from, lines.size())));
          } // else already logged as error
        } else {
          log.warn("Startup output file {} does not exist (on success).", outputFilePath);
        }
      } catch (Exception logEx) {
        log.warn("Failed to read startup output file (on success): {}", logEx.getMessage(), logEx);
      }
    }
  }

  @NotNull
  private Map<String, String> getEnvVarsMap() {
    final Map<String, String> envVars = new HashMap<>();
    if (databaseType == DatabaseType.ELASTICSEARCH) {
      envVars.put("OPTIMIZE_ELASTICSEARCH_HTTP_PORT", String.valueOf(databasePort));
      envVars.put("CAMUNDA_OPTIMIZE_ELASTICSEARCH_SETTINGS_INDEX_NUMBER_OF_REPLICAS", "0");
      envVars.put("CAMUNDA_OPTIMIZE_DATABASE", ELASTICSEARCH_DATABASE_PROPERTY);
    } else if (databaseType == DatabaseType.OPENSEARCH) {
      envVars.put("CAMUNDA_OPTIMIZE_OPENSEARCH_HTTP_PORT", String.valueOf(databasePort));
      envVars.put("CAMUNDA_OPTIMIZE_OPENSEARCH_SETTINGS_INDEX_NUMBER_OF_REPLICAS", "0");
      envVars.put("CAMUNDA_OPTIMIZE_DATABASE", OPENSEARCH_DATABASE_PROPERTY);
    }
    envVars.put("OPTIMIZE_API_ACCESS_TOKEN", "secret");
    envVars.put("SPRING_PROFILES_ACTIVE", "ccsm");
    return envVars;
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
