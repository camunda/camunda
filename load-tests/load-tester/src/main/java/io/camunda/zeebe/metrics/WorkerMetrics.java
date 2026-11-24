/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import io.camunda.zeebe.metrics.StarterMetricsDoc.StarterMetricKeyNames;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Records the outcome of the commands the worker sends, which the client does not expose. */
public final class WorkerMetrics {

  private static final String OUTCOME_SUCCESS = "success";
  private static final String OUTCOME_FAILURE = "failure";
  private static final String NO_ERROR = "none";

  private final MeterRegistry registry;
  private final Map<TimerKey, Timer> timers = new ConcurrentHashMap<>();
  private final Counter trackingDropped;

  public WorkerMetrics(final MeterRegistry registry) {
    this.registry = registry;
    trackingDropped =
        Counter.builder(WorkerMetricsDoc.COMPLETION_TRACKING_DROPPED.getName())
            .description(WorkerMetricsDoc.COMPLETION_TRACKING_DROPPED.getDescription())
            .register(registry);
    // register the success series up front so dashboards show 0 instead of no data
    timer(WorkerMetricsDoc.JOB_COMPLETION, OUTCOME_SUCCESS, NO_ERROR);
    timer(WorkerMetricsDoc.MESSAGE_PUBLISH, OUTCOME_SUCCESS, NO_ERROR);
  }

  /** Records a finished completion command; {@code error} is null on success. */
  public void recordCompletion(final long startNanos, final Throwable error) {
    record(WorkerMetricsDoc.JOB_COMPLETION, startNanos, error);
  }

  /** Records a finished message publish; {@code error} is null on success. */
  public void recordMessagePublish(final long startNanos, final Throwable error) {
    record(WorkerMetricsDoc.MESSAGE_PUBLISH, startNanos, error);
  }

  public void recordCompletionTrackingDropped() {
    trackingDropped.increment();
  }

  private void record(final WorkerMetricsDoc doc, final long startNanos, final Throwable error) {
    final long duration = System.nanoTime() - startNanos;
    final var timer =
        error == null
            ? timer(doc, OUTCOME_SUCCESS, NO_ERROR)
            : timer(doc, OUTCOME_FAILURE, ErrorType.of(error));
    timer.record(duration, TimeUnit.NANOSECONDS);
  }

  private Timer timer(final WorkerMetricsDoc doc, final String outcome, final String error) {
    return timers.computeIfAbsent(
        new TimerKey(doc, outcome, error),
        key ->
            MicrometerUtil.buildTimer(doc)
                .tag(StarterMetricKeyNames.OUTCOME.asString(), outcome)
                .tag(StarterMetricKeyNames.ERROR.asString(), error)
                .register(registry));
  }

  private record TimerKey(WorkerMetricsDoc doc, String outcome, String error) {}
}
