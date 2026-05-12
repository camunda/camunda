/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.optimize;

import io.camunda.zeebe.config.OptimizeProperties;
import io.camunda.zeebe.optimize.OptimizeApiClient.DetailedPageResult;
import io.camunda.zeebe.optimize.OptimizeApiClient.HomepageResult;
import io.camunda.zeebe.optimize.OptimizeApiClient.ReportEvaluationResult;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schedules periodic Optimize dashboard and report evaluations to exercise the Optimize REST API
 * under load. Owns an {@link OptimizeApiClient} and a single-threaded scheduler.
 *
 * <p>No Micrometer metrics are emitted; Optimize already publishes its own report-evaluation
 * timers. Failures are logged (throttled) and the next cycle continues.
 *
 * <p>Started from {@code Starter} when {@code load-tester.optimize.enabled=true}. Synchronous
 * execution per cycle (matches {@link io.camunda.zeebe.read.DataReadMeter}) to avoid pile-up when
 * Optimize is slow.
 */
public class OptimizeReportEvaluator implements AutoCloseable {

  static final String PAGE_HOMEPAGE = "homepage";
  static final String PAGE_DETAILED = "detailed";
  static final String PHASE_DASHBOARD = "dashboard";
  static final String PHASE_REPORT_EVALUATE = "report_evaluate";
  static final String NA = "n/a";

  private static final Logger LOG = LoggerFactory.getLogger(OptimizeReportEvaluator.class);
  private static final ThrottledLogger THROTTLED_LOGGER =
      new ThrottledLogger(LOG, Duration.ofSeconds(5));

  private final OptimizeProperties props;
  private final OptimizeApiClient client;
  private final ScheduledExecutorService executor;
  private final AtomicReference<String> processDefinitionKey = new AtomicReference<>();
  private ScheduledFuture<?> scheduledTask;

  public OptimizeReportEvaluator(
      final OptimizeProperties props,
      final OptimizeApiClient client,
      final ScheduledExecutorService executor) {
    this.props = props;
    this.client = client;
    this.executor = executor;
  }

  public void start() {
    authenticateWithRetry();

    LOG.info(
        "Scheduling Optimize dashboard and report evaluations every {} (initial delay {})",
        props.getEvaluationInterval(),
        props.getInitialDelay());

    scheduledTask =
        executor.scheduleAtFixedRate(
            this::runOneCycleSafely,
            props.getInitialDelay().toMillis(),
            props.getEvaluationInterval().toMillis(),
            TimeUnit.MILLISECONDS);
  }

  void runOneCycleSafely() {
    try {
      runOneCycle();
    } catch (final Exception e) {
      THROTTLED_LOGGER.error("Error during Optimize evaluation cycle", e);
    }
  }

  private void runOneCycle() {
    evaluateHomepage();
    evaluateDetailedPage();
  }

  private void evaluateHomepage() {
    try {
      final HomepageResult result = client.evaluateHomepage();
      logEvaluation(
          PAGE_HOMEPAGE,
          PHASE_DASHBOARD,
          NA,
          result.dashboardStatusCode(),
          result.dashboardResponseTimeMs());
      logReports(PAGE_HOMEPAGE, PHASE_REPORT_EVALUATE, result.reportResults());
    } catch (final Exception e) {
      THROTTLED_LOGGER.warn("Failed to evaluate Optimize homepage", e);
    }
  }

  private void evaluateDetailedPage() {
    final String pdKey;
    try {
      pdKey = resolveProcessDefinitionKey();
    } catch (final Exception e) {
      THROTTLED_LOGGER.warn("Failed to resolve process definition key for detailed evaluation", e);
      return;
    }
    if (pdKey == null || pdKey.isBlank()) {
      return;
    }
    try {
      final DetailedPageResult result = client.evaluateDetailedPage(pdKey);
      logEvaluation(
          PAGE_DETAILED,
          PHASE_DASHBOARD,
          NA,
          result.dashboardStatusCode(),
          result.dashboardResponseTimeMs());
      logReports(PAGE_DETAILED, PHASE_REPORT_EVALUATE, result.reportEvaluationResults());
    } catch (final Exception e) {
      THROTTLED_LOGGER.warn("Failed to evaluate Optimize detailed page", e);
    }
  }

  private void logReports(
      final String page, final String phase, final List<ReportEvaluationResult> reports) {
    for (final ReportEvaluationResult report : reports) {
      logEvaluation(page, phase, report.reportId(), report.statusCode(), report.responseTimeMs());
    }
  }

  private void logEvaluation(
      final String page,
      final String phase,
      final String reportId,
      final int statusCode,
      final long responseTimeMs) {
    if (statusCode >= 200 && statusCode < 300) {
      LOG.debug(
          "Optimize {} {} report={} status={} timeMs={}",
          page,
          phase,
          reportId,
          statusCode,
          responseTimeMs);
    } else {
      THROTTLED_LOGGER.warn(
          "Optimize {} {} report={} non-2xx status={} timeMs={}",
          page,
          phase,
          reportId,
          statusCode,
          responseTimeMs);
    }
  }

  private String resolveProcessDefinitionKey() {
    final String configured = props.getProcessDefinitionKey();
    if (configured != null && !configured.isBlank()) {
      return configured;
    }
    final String cached = processDefinitionKey.get();
    if (cached != null) {
      return cached;
    }
    final String fetched = client.fetchFirstProcessDefinitionKey();
    processDefinitionKey.compareAndSet(null, fetched);
    return processDefinitionKey.get();
  }

  void authenticateWithRetry() {
    final int maxAttempts = props.getAuthRetryMaxAttempts();
    final long delayMillis = props.getAuthRetryDelay().toMillis();

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        LOG.info("Authenticating with Optimize Keycloak (attempt {}/{})", attempt, maxAttempts);
        client.authenticate();
        LOG.info("Optimize successfully authenticated");
        return;
      } catch (final RuntimeException e) {
        if (attempt == maxAttempts) {
          LOG.error("Failed to authenticate with Optimize after {} attempts", maxAttempts, e);
          throw new IllegalStateException(
              "Optimize authentication failed after " + maxAttempts + " attempts", e);
        }
        THROTTLED_LOGGER.warn(
            "Failed to authenticate with Optimize (attempt {}/{}), retrying in {}ms",
            attempt,
            maxAttempts,
            delayMillis,
            e);
        sleep(delayMillis);
      }
    }
  }

  private static void sleep(final long millis) {
    try {
      Thread.sleep(millis);
    } catch (final InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while waiting to retry Optimize authentication", ex);
    }
  }

  @Override
  public void close() {
    if (scheduledTask != null) {
      scheduledTask.cancel(true);
    }
    executor.shutdownNow();
  }
}
