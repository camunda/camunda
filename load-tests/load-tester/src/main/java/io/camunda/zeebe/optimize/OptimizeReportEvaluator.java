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
import io.camunda.zeebe.optimize.OptimizeApiClient.PageEvaluationResult;
import io.camunda.zeebe.optimize.OptimizeApiClient.ReportEvaluationResult;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodically evaluates Optimize dashboards and reports to exercise the Optimize REST API under
 * load, publishing per-request latency and status metrics. Failures are throttled-logged and the
 * next cycle continues.
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
  private final MeterRegistry registry;
  private final AtomicReference<String> processDefinitionKey = new AtomicReference<>();
  // Lazily registered per tag-combination.
  private final ConcurrentHashMap<String, Timer> latencyTimers = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Counter> requestCounters = new ConcurrentHashMap<>();
  private ScheduledFuture<?> scheduledTask;

  public OptimizeReportEvaluator(
      final OptimizeProperties props,
      final OptimizeApiClient client,
      final ScheduledExecutorService executor,
      final MeterRegistry registry) {
    this.props = props;
    this.client = client;
    this.executor = executor;
    this.registry = registry;
  }

  public void start() {
    LOG.info(
        "Scheduling Optimize dashboard and report evaluations every {} (initial delay {})",
        props.getEvaluationInterval(),
        props.getInitialDelay());
    scheduleCycle(props.getInitialDelay().toMillis());
  }

  /**
   * Schedules a single evaluation cycle after {@code delayMillis}. Unlike {@code
   * scheduleAtFixedRate}, the next cycle is only scheduled once the current one finishes (see
   * {@link #runAndReschedule()}), so the blocking HTTP cycles never overlap or pile up.
   */
  private void scheduleCycle(final long delayMillis) {
    if (executor.isShutdown()) {
      return;
    }
    scheduledTask = executor.schedule(this::runAndReschedule, delayMillis, TimeUnit.MILLISECONDS);
  }

  private void runAndReschedule() {
    final long startNanos = System.nanoTime();
    runOneCycleSafely();
    final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    // Only reschedule AFTER the previous cycle is done, to avoid overlapping cycles. If a cycle
    // already took longer than the interval, run the next one immediately.
    final long intervalMillis = props.getEvaluationInterval().toMillis();
    final long nextDelay = elapsedMillis >= intervalMillis ? 0 : intervalMillis - elapsedMillis;
    scheduleCycle(nextDelay);
  }

  void runOneCycleSafely() {
    try {
      evaluateHomepage();
      evaluateDetailedPage();
    } catch (final Exception e) {
      THROTTLED_LOGGER.error("Error during Optimize evaluation cycle", e);
    }
  }

  private void evaluateHomepage() {
    try {
      final PageEvaluationResult result = client.evaluateHomepage();
      recordEvaluation(
          PAGE_HOMEPAGE,
          PHASE_DASHBOARD,
          NA,
          result.dashboardStatusCode(),
          result.dashboardResponseTimeMs());
      recordReports(PAGE_HOMEPAGE, result.reportResults());
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
      final PageEvaluationResult result = client.evaluateDetailedPage(pdKey);
      recordEvaluation(
          PAGE_DETAILED,
          PHASE_DASHBOARD,
          NA,
          result.dashboardStatusCode(),
          result.dashboardResponseTimeMs());
      recordReports(PAGE_DETAILED, result.reportResults());
    } catch (final Exception e) {
      THROTTLED_LOGGER.warn("Failed to evaluate Optimize detailed page", e);
    }
  }

  private void recordReports(final String page, final List<ReportEvaluationResult> reports) {
    for (final ReportEvaluationResult report : reports) {
      recordEvaluation(
          page,
          PHASE_REPORT_EVALUATE,
          report.reportId(),
          report.statusCode(),
          report.responseTimeMs());
    }
  }

  private void recordEvaluation(
      final String page,
      final String phase,
      final String reportId,
      final int statusCode,
      final long responseTimeMs) {
    latencyTimer(page, phase, reportId).record(responseTimeMs, TimeUnit.MILLISECONDS);
    requestCounter(page, phase, statusCode).increment();
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

  private Timer latencyTimer(final String page, final String phase, final String reportId) {
    return latencyTimers.computeIfAbsent(
        page + "|" + phase + "|" + reportId,
        key ->
            MicrometerUtil.buildTimer(OptimizeMetricsDoc.REPORT_EVALUATION_LATENCY)
                .tag(OptimizeMetricKeyNames.PAGE.asString(), page)
                .tag(OptimizeMetricKeyNames.PHASE.asString(), phase)
                .tag(OptimizeMetricKeyNames.REPORT_ID.asString(), reportId)
                .register(registry));
  }

  private Counter requestCounter(final String page, final String phase, final int statusCode) {
    final String status = Integer.toString(statusCode);
    return requestCounters.computeIfAbsent(
        page + "|" + phase + "|" + status,
        key ->
            Counter.builder(OptimizeMetricsDoc.REPORT_EVALUATION_REQUESTS.getName())
                .description(OptimizeMetricsDoc.REPORT_EVALUATION_REQUESTS.getDescription())
                .tag(OptimizeMetricKeyNames.PAGE.asString(), page)
                .tag(OptimizeMetricKeyNames.PHASE.asString(), phase)
                .tag(OptimizeMetricKeyNames.STATUS_CODE.asString(), status)
                .register(registry));
  }

  @Override
  public void close() {
    if (scheduledTask != null) {
      scheduledTask.cancel(true);
    }
    executor.shutdownNow();
  }
}
