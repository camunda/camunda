/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import io.camunda.zeebe.config.OptimizeCfg;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OptimizeReportStartMeter implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(OptimizeReportStartMeter.class);
  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(
          LoggerFactory.getLogger(OptimizeReportStartMeter.class), Duration.ofSeconds(5));

  private final OptimizeCfg optimizeCfg;
  private final OptimizeReportApiClient optimizeLoadTester;
  private final ScheduledExecutorService executorService;

  public OptimizeReportStartMeter(final OptimizeCfg optimizeCfg) {
    this.optimizeCfg = optimizeCfg;
    executorService = Executors.newScheduledThreadPool(1);
    optimizeLoadTester = new OptimizeReportApiClient(optimizeCfg);
  }

  public void start() {
    authenticateWithRetry();

    final int intervalSeconds = optimizeCfg.getEvaluationIntervalSeconds();
    LOG.info(
        "Scheduling Optimize dashboard and report evaluations every {} seconds", intervalSeconds);

    executorService.scheduleAtFixedRate(
        () -> {
          try {
            evaluateHomepage();
            evaluateDetailedPage();
          } catch (final Exception e) {
            THROTTLED_LOGGER.error("Error during Optimize evaluation cycle", e);
          }
        },
        10,
        intervalSeconds,
        TimeUnit.SECONDS);

    LOG.info("Optimize evaluation meter started");
  }

  private void authenticateWithRetry() {
    final int maxAttempts = optimizeCfg.getAuthRetryMaxAttempts();
    final int delaySeconds = optimizeCfg.getAuthRetryDelaySeconds();

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        LOG.info("Authenticating Optimize with Keycloak (attempt {}/{})", attempt, maxAttempts);
        optimizeLoadTester.authenticateWithAuthorizationCodeFlow();
        LOG.info("Optimize successfully authenticated");
        return;
      } catch (final Exception e) {
        if (attempt == maxAttempts) {
          LOG.error("Failed to authenticate Optimize after {} attempts", maxAttempts, e);
          throw new RuntimeException(
              "Optimize authentication failed after " + maxAttempts + " attempts", e);
        }
        THROTTLED_LOGGER.warn(
            "Failed to authenticate Optimize (attempt {}/{}), retrying in {}s",
            attempt,
            maxAttempts,
            delaySeconds,
            e);
        try {
          Thread.sleep(delaySeconds * 1000L);
        } catch (final InterruptedException ex) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(
              "Interrupted while waiting to retry Optimize authentication", ex);
        }
      }
    }
  }

  private void evaluateHomepage() {
    try {
      final OptimizeReportApiClient.HomepageResult result = optimizeLoadTester.evaluateHomepage();

      LOG.info(
          "Homepage evaluation completed — Dashboard: {}ms, Reports: {}, Max report: {}ms, Homepage load: {}ms, Total: {}ms",
          result.getDashboardResult().getResponseTimeMs(),
          result.getReportResults().size(),
          result.getMaxReportTimeMs(),
          result.getHomepageLoadTimeMs(),
          result.getTotalResponseTimeMs());

    } catch (final Exception e) {
      THROTTLED_LOGGER.error("Failed to evaluate Optimize dashboard and reports", e);
    }
  }

  private void evaluateDetailedPage() {
    try {
      final String processDefinitionKey = optimizeLoadTester.fetchFirstProcessDefinitionKey();
      final OptimizeReportApiClient.DetailedPageResult result =
          optimizeLoadTester.evaluateDetailedPage(processDefinitionKey);

      LOG.info(
          "Detailed Dashboard evaluation completed — Dashboard: {}ms, Report evals: {}, Detailed evals: {}, Max report: {}ms, Max detailed: {}ms, Total: {}ms",
          result.getDashboardResult().getResponseTimeMs(),
          result.getReportEvaluationResults().size(),
          result.getDetailedEvaluationResults().size(),
          result.getMaxReportEvaluationTimeMs(),
          result.getMaxDetailedEvaluationTimeMs(),
          result.getTotalResponseTimeMs());

    } catch (final Exception e) {
      THROTTLED_LOGGER.error("Failed to evaluate detailed page", e);
    }
  }

  @Override
  public void close() {
    executorService.shutdownNow();
    optimizeLoadTester.close();
  }
}
