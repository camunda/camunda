/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.starter;

import io.camunda.zeebe.metrics.ErrorType;
import io.camunda.zeebe.metrics.StarterMetricsDoc;
import io.camunda.zeebe.metrics.StarterMetricsDoc.StarterMetricKeyNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** Counts the answers to process instance start requests by outcome and error type. */
final class StarterResultMetrics {

  private static final String NONE = "none";

  private final MeterRegistry registry;
  private final Counter successCounter;

  StarterResultMetrics(final MeterRegistry registry) {
    this.registry = registry;
    successCounter = counter("success", NONE);
  }

  /**
   * Records an answer.
   *
   * @param error the failure of the request, or {@code null} if it succeeded
   */
  void record(final Throwable error) {
    if (error == null) {
      successCounter.increment();
    } else {
      counter("failure", ErrorType.of(error)).increment();
    }
  }

  private Counter counter(final String outcome, final String error) {
    return Counter.builder(StarterMetricsDoc.PROCESS_INSTANCES_STARTED.getName())
        .description(StarterMetricsDoc.PROCESS_INSTANCES_STARTED.getDescription())
        .tag(StarterMetricKeyNames.OUTCOME.asString(), outcome)
        .tag(StarterMetricKeyNames.ERROR.asString(), error)
        .register(registry);
  }
}
