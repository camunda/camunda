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
import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;

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

    final ProcessBuilder processBuilder =
        new ProcessBuilder()
            .command("bash", "-c", "./optimize-startup.sh")
            .redirectOutput(Redirect.to(new File(outputFilePath)))
            // Add error stream redirection to capture startup errors
            .redirectErrorStream(true)
            .directory(new File(optimizeDirectory));
    final Map<String, String> envVars = getEnvVarsMap();

    processBuilder.environment().putAll(envVars);
    process = processBuilder.start();

    try {
      final HealthClient healthClient = new HealthClient(() -> requestExecutor);
      log.info("Waiting for Optimize {} to boot...", optimizeVersion);

      // Add process monitoring to detect if Optimize crashes
      Thread processMonitor =
          new Thread(
              () -> {
                try {
                  int exitCode = process.waitFor();
                  if (exitCode != 0) {
                    log.error("Optimize process exited with code: {}", exitCode);
                    // Try to read the output file for error details
                    try {
                      List<String> lines = Files.readAllLines(Paths.get(outputFilePath));
                      log.error("Last 20 lines of startup log:");
                      lines.stream().skip(Math.max(0, lines.size() - 20)).forEach(log::error);
                    } catch (Exception e) {
                      log.error("Could not read startup log: {}", e.getMessage());
                    }
                  }
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });
      processMonitor.setDaemon(true);
      processMonitor.start();

      await()
          // this delay is here for avoiding race conditions of still running initializations
          .pollDelay(20, SECONDS)
          .pollInterval(5, SECONDS)
          .atMost(60, SECONDS)
          .ignoreException(ProcessingException.class)
          .until(
              () -> {
                // Check if process is still alive
                if (!process.isAlive()) {
                  log.error("Optimize process died during startup!");
                  return healthClient.getReadiness();
                }
                return healthClient.getReadiness();
              },
              response -> HttpStatus.OK.value() == response.getStatus());
      log.info("Optimize {} is up!", optimizeVersion);
    } catch (final Exception e) {
      log.error("Optimize did not start within 60s.");

      // Enhanced error logging
      if (process.isAlive()) {
        log.error("Process is still running but not responding to health checks");
      } else {
        log.error("Process has died during startup");
      }

      // Try to capture the last few lines of the startup log
      try {
        List<String> lines = Files.readAllLines(Paths.get(outputFilePath));
        log.error("Last 10 lines of startup log:");
        lines.stream().skip(Math.max(0, lines.size() - 10)).forEach(log::error);
      } catch (Exception logException) {
        log.error("Could not read startup log: {}", logException.getMessage());
      }

      stop();
      throw e;
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
