/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.worker;

import io.camunda.zeebe.metrics.ErrorType;
import io.camunda.zeebe.metrics.ResultMetricKeyNames;
import io.camunda.zeebe.metrics.WorkerMetricsDoc;
import io.camunda.zeebe.metrics.WorkerMetricsDoc.WorkerMetricKeyNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;

/** Counts the results of the requests sent by the worker by outcome and error type. */
final class WorkerResultMetrics {

  private static final String NONE = "";

  private final MeterRegistry registry;
  private final String jobType;
  private final Counter publicationSuccess;
  private final Counter completionSuccess;
  private final Counter trackingDropped;

  WorkerResultMetrics(
      final MeterRegistry registry, final String jobType, final Collection<?> pendingCompletions) {
    this.registry = registry;
    this.jobType = jobType;
    trackingDropped =
        Counter.builder(WorkerMetricsDoc.JOB_COMPLETION_TRACKING_DROPPED.getName())
            .description(WorkerMetricsDoc.JOB_COMPLETION_TRACKING_DROPPED.getDescription())
            .tag(WorkerMetricKeyNames.JOB_TYPE.asString(), jobType)
            .register(registry);
    Gauge.builder(
            WorkerMetricsDoc.JOB_COMPLETIONS_PENDING.getName(),
            pendingCompletions,
            Collection::size)
        .description(WorkerMetricsDoc.JOB_COMPLETIONS_PENDING.getDescription())
        .tag(WorkerMetricKeyNames.JOB_TYPE.asString(), jobType)
        .register(registry);
    publicationSuccess = counter(WorkerMetricsDoc.MESSAGE_PUBLICATIONS, "success", NONE);
    completionSuccess = counter(WorkerMetricsDoc.JOB_COMPLETIONS, "success", NONE);
  }

  void recordMessagePublication() {
    publicationSuccess.increment();
  }

  void recordMessagePublication(final Throwable error) {
    counter(WorkerMetricsDoc.MESSAGE_PUBLICATIONS, "failure", ErrorType.of(error)).increment();
  }

  void recordJobCompletion() {
    completionSuccess.increment();
  }

  void recordJobCompletion(final Throwable error) {
    counter(WorkerMetricsDoc.JOB_COMPLETIONS, "failure", ErrorType.of(error)).increment();
  }

  /** Counts a completion whose result will never be recorded because the queue was full. */
  void recordTrackingDropped() {
    trackingDropped.increment();
  }

  private Counter counter(final WorkerMetricsDoc doc, final String outcome, final String error) {
    return Counter.builder(doc.getName())
        .description(doc.getDescription())
        .tag(WorkerMetricKeyNames.JOB_TYPE.asString(), jobType)
        .tag(ResultMetricKeyNames.OUTCOME.asString(), outcome)
        .tag(ResultMetricKeyNames.ERROR.asString(), error)
        .register(registry);
  }
}
