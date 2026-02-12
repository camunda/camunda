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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OptimizeEvaluationMeter implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(OptimizeEvaluationMeter.class);
  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(
          LoggerFactory.getLogger(OptimizeEvaluationMeter.class), Duration.ofSeconds(5));

  private final OptimizeCfg optimizeCfg;
  private final OptimizeReportLoadTester optimizeLoadTester;
  private final ScheduledExecutorService executorService;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  private ScheduledFuture<?> scheduledTask;

  public OptimizeEvaluationMeter(final OptimizeCfg optimizeCfg) {
    this.optimizeCfg = optimizeCfg;
    executorService = Executors.newScheduledThreadPool(1);

    // Create load tester instance
    optimizeLoadTester =
        new OptimizeReportLoadTester(
            optimizeCfg.getBaseUrl(),
            optimizeCfg.getKeycloakUrl(),
            optimizeCfg.getRealm(),
            optimizeCfg.getClientId(),
            optimizeCfg.getUsername(),
            optimizeCfg.getPassword(),
            optimizeCfg.getClientSecret());
  }

  /** Authenticates with Keycloak and starts periodic Optimize evaluations. */
  public void start() {
    authenticateWithRetry();

    final int intervalSeconds = optimizeCfg.getEvaluationIntervalSeconds();
    LOG.info(
        "Scheduling Optimize dashboard and report evaluations every {} seconds", intervalSeconds);

    final BooleanSupplier shouldContinue = createContinuationCondition();

    scheduledTask =
        executorService.scheduleAtFixedRate(
            () -> {
              if (!shouldContinue.getAsBoolean()) {
                return;
              }

              try {
                evaluateDashboardAndReports();
                evaluateInstantBenchmark();
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

  private BooleanSupplier createContinuationCondition() {
    final int durationLimit = optimizeCfg.getDurationLimit();

    if (durationLimit > 0) {
      final LocalDateTime endTime = LocalDateTime.now().plus(durationLimit, ChronoUnit.SECONDS);
      return () -> LocalDateTime.now().isBefore(endTime);
    } else {
      return () -> true;
    }
  }

  private void evaluateDashboardAndReports() {
    LOG.info("Starting Optimize evaluation cycle");

    try {
      optimizeLoadTester.ensureValidToken();

      final OptimizeReportLoadTester.DashboardWithReportsResult result =
          optimizeLoadTester.evaluateDashboardWithReports();

      final OptimizeReportLoadTester.DashboardEvaluationResult dashboardResult =
          result.getDashboardResult();
      logDashboardResult(dashboardResult, "Dashboard");

      final List<OptimizeReportLoadTester.ReportEvaluationResult> reportResults =
          result.getReportResults();
      logReportResults(
          reportResults,
          "Report {} [{}] evaluated successfully in {}ms",
          "Report {} [{}] evaluation failed with status {}");

      LOG.info(
          "Optimize evaluation cycle completed - Dashboard: {}ms, Reports: {}, Max report: {}ms, Homepage load: {}ms, Total: {}ms",
          dashboardResult.getResponseTimeMs(),
          reportResults.size(),
          result.getMaxReportTimeMs(),
          result.getHomepageLoadTimeMs(),
          result.getTotalResponseTimeMs());

    } catch (final Exception e) {
      THROTTLED_LOGGER.error("Failed to evaluate Optimize dashboard and reports", e);
    }
  }

  private void evaluateInstantBenchmark() {
    LOG.info("Starting Optimize instant benchmark evaluation cycle");

    try {
      optimizeLoadTester.ensureValidToken();

      final OptimizeReportLoadTester.InstantBenchmarkResult result =
          optimizeLoadTester.evaluateInstantBenchmark(optimizeCfg.getProcessDefinitionKey());

      final OptimizeReportLoadTester.DashboardEvaluationResult dashboardResult =
          result.getDashboardResult();
      logDashboardResult(dashboardResult, "Benchmark dashboard");

      final List<OptimizeReportLoadTester.ReportEvaluationResult> reportEvalResults =
          result.getReportEvaluationResults();
      logReportResults(
          reportEvalResults,
          "Benchmark report {} [{}] evaluated successfully in {}ms",
          "Benchmark report {} [{}] evaluation failed with status {}");

      final List<OptimizeReportLoadTester.ReportEvaluationResult> detailedResults =
          result.getDetailedEvaluationResults();
      logReportResults(
          detailedResults,
          "Benchmark detailed evaluation for report {} [{}] completed in {}ms",
          "Benchmark detailed evaluation for report {} [{}] failed with status {}");

      LOG.info(
          "Optimize instant benchmark cycle completed - Dashboard: {}ms, Report evaluations: {}, Detailed evaluations: {}, Max report eval: {}ms, Max detailed eval: {}ms, Total: {}ms",
          dashboardResult.getResponseTimeMs(),
          reportEvalResults.size(),
          detailedResults.size(),
          result.getMaxReportEvaluationTimeMs(),
          result.getMaxDetailedEvaluationTimeMs(),
          result.getTotalResponseTimeMs());

    } catch (final Exception e) {
      THROTTLED_LOGGER.error("Failed to evaluate Optimize instant benchmark", e);
    }
  }

  private void logDashboardResult(
      final OptimizeReportLoadTester.DashboardEvaluationResult dashboardResult,
      final String logPrefix) {
    if (dashboardResult.isSuccess()) {
      LOG.info("{} evaluated successfully in {}ms", logPrefix, dashboardResult.getResponseTimeMs());
    } else {
      LOG.error("{} evaluation failed with status {}", logPrefix, dashboardResult.getStatusCode());
    }
  }

  private void logReportResults(
      final List<OptimizeReportLoadTester.ReportEvaluationResult> reportResults,
      final String successLogTemplate,
      final String errorLogTemplate) {
    for (final OptimizeReportLoadTester.ReportEvaluationResult reportResult : reportResults) {
      final String reportName =
          reportResult.getReportName() != null ? reportResult.getReportName() : "unknown";

      if (reportResult.isSuccess()) {
        LOG.info(
            successLogTemplate,
            reportResult.getReportId(),
            reportName,
            reportResult.getResponseTimeMs());
      } else {
        LOG.error(
            errorLogTemplate, reportResult.getReportId(), reportName, reportResult.getStatusCode());
      }
    }
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    if (scheduledTask != null) {
      scheduledTask.cancel(true);
    }
    executorService.shutdown();
    try {
      executorService.awaitTermination(60, TimeUnit.SECONDS);
    } catch (final InterruptedException e) {
      LOG.error("Shutdown of Optimize evaluation executor was interrupted", e);
      Thread.currentThread().interrupt();
    }
    LOG.info("Optimize evaluation meter stopped");
  }
}
