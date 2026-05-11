/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.optimize;

import io.camunda.zeebe.config.OptimizeProperties;
import io.camunda.zeebe.metrics.OptimizeMetricsDoc;
import io.camunda.zeebe.metrics.OptimizeMetricsDoc.OptimizeMetricKeyNames;
import io.camunda.zeebe.optimize.OptimizeApiClient.DetailedPageResult;
import io.camunda.zeebe.optimize.OptimizeApiClient.HomepageResult;
import io.camunda.zeebe.optimize.OptimizeApiClient.OptimizeAuthException;
import io.camunda.zeebe.optimize.OptimizeApiClient.ReportEvaluationResult;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schedules periodic Optimize report evaluations and records latency and failure metrics. Owns an
 * {@link OptimizeApiClient} and a single-threaded scheduler.
 *
 * <p>Started from {@code Starter} when {@code load-tester.optimize.enabled=true}. Synchronous
 * execution per cycle (matches {@link io.camunda.zeebe.read.DataReadMeter}) to avoid pile-up when
 * Optimize is slow under load.
 */
public class OptimizeReportEvaluator implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(OptimizeReportEvaluator.class);
  private static final ThrottledLogger THROTTLED_LOGGER =
      new ThrottledLogger(LOG, Duration.ofSeconds(5));

  private static final String PAGE_HOMEPAGE = "homepage";
  private static final String PAGE_DETAILED = "detailed";
  private static final String PHASE_DASHBOARD = "dashboard";
  private static final String PHASE_REPORT_EVALUATE = "report_evaluate";
  private static final String PHASE_DETAILED_EVALUATE = "detailed_evaluate";
  private static final String NA = "n/a";

  private final OptimizeProperties props;
  private final OptimizeApiClient client;
  private final MeterRegistry registry;
  private final ScheduledExecutorService executor;
  private final AtomicReference<String> processDefinitionKey = new AtomicReference<>();
  private ScheduledFuture<?> scheduledTask;

  public OptimizeReportEvaluator(
      final OptimizeProperties props,
      final OptimizeApiClient client,
      final MeterRegistry registry,
      final ScheduledExecutorService executor) {
    this.props = props;
    this.client = client;
    this.registry = registry;
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
      recordEvaluation(
          PAGE_HOMEPAGE,
          PHASE_DASHBOARD,
          NA,
          result.dashboardStatusCode(),
          result.dashboardResponseTimeMs());
      for (final ReportEvaluationResult report : result.reportResults()) {
        recordEvaluation(
            PAGE_HOMEPAGE,
            PHASE_REPORT_EVALUATE,
            report.reportId(),
            report.statusCode(),
            report.responseTimeMs());
      }
    } catch (final Exception e) {
      THROTTLED_LOGGER.error("Failed to evaluate Optimize homepage", e);
    }
  }

  private void evaluateDetailedPage() {
    final String pdKey;
    try {
      pdKey = resolveProcessDefinitionKey();
    } catch (final Exception e) {
      THROTTLED_LOGGER.error("Failed to resolve process definition key for detailed evaluation", e);
      return;
    }
    if (pdKey == null || pdKey.isBlank()) {
      return;
    }
    try {
      final DetailedPageResult result = client.evaluateDetailedPage(pdKey);
      recordEvaluation(
          PAGE_DETAILED,
          PHASE_DASHBOARD,
          NA,
          result.dashboardStatusCode(),
          result.dashboardResponseTimeMs());
      recordReports(PAGE_DETAILED, PHASE_REPORT_EVALUATE, result.reportEvaluationResults());
      recordReports(PAGE_DETAILED, PHASE_DETAILED_EVALUATE, result.detailedEvaluationResults());
    } catch (final Exception e) {
      THROTTLED_LOGGER.error("Failed to evaluate Optimize detailed page", e);
    }
  }

  private void recordReports(
      final String page, final String phase, final List<ReportEvaluationResult> reports) {
    for (final ReportEvaluationResult report : reports) {
      recordEvaluation(
          page, phase, report.reportId(), report.statusCode(), report.responseTimeMs());
    }
  }

  private void recordEvaluation(
      final String page,
      final String phase,
      final String reportId,
      final int statusCode,
      final long responseTimeMs) {
    if (statusCode >= 200 && statusCode < 300) {
      latencyTimer(page, phase, reportId).record(responseTimeMs, TimeUnit.MILLISECONDS);
    } else {
      failureCounter(page, phase, statusCode).increment();
      LOG.debug(
          "Optimize evaluation non-2xx: page={} phase={} report={} status={}",
          page,
          phase,
          reportId,
          statusCode);
    }
  }

  private Timer latencyTimer(final String page, final String phase, final String reportId) {
    return MicrometerUtil.buildTimer(OptimizeMetricsDoc.REPORT_EVALUATION_LATENCY)
        .tag(OptimizeMetricKeyNames.PAGE.asString(), page)
        .tag(OptimizeMetricKeyNames.PHASE.asString(), phase)
        .tag(OptimizeMetricKeyNames.REPORT_ID.asString(), reportId)
        .register(registry);
  }

  private Counter failureCounter(final String page, final String phase, final int statusCode) {
    return Counter.builder(OptimizeMetricsDoc.REPORT_EVALUATION_FAILURES.getName())
        .description(OptimizeMetricsDoc.REPORT_EVALUATION_FAILURES.getDescription())
        .tag(OptimizeMetricKeyNames.PAGE.asString(), page)
        .tag(OptimizeMetricKeyNames.PHASE.asString(), phase)
        .tag(OptimizeMetricKeyNames.STATUS_CODE.asString(), Integer.toString(statusCode))
        .register(registry);
  }

  private Counter authFailureCounter(final String reason) {
    return Counter.builder(OptimizeMetricsDoc.AUTH_FAILURES.getName())
        .description(OptimizeMetricsDoc.AUTH_FAILURES.getDescription())
        .tag(OptimizeMetricKeyNames.REASON.asString(), reason)
        .register(registry);
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
        client.authenticateWithPasswordGrant();
        LOG.info("Optimize successfully authenticated");
        return;
      } catch (final OptimizeAuthException e) {
        authFailureCounter(reasonFor(e.getStatusCode())).increment();
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
      } catch (final Exception e) {
        authFailureCounter("other").increment();
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

  private static String reasonFor(final int statusCode) {
    if (statusCode == 0) {
      return "network";
    }
    if (statusCode >= 400 && statusCode < 500) {
      return "http_4xx";
    }
    if (statusCode >= 500 && statusCode < 600) {
      return "http_5xx";
    }
    return "other";
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
