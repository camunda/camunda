/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import io.camunda.analytics.query.QueryMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * {@link QueryMetrics} as a Micrometer {@code analytics.query.duration} timer, one per dataset,
 * lazily created (the dashboard read path calls in from HTTP request threads, so the per-dataset
 * timer cache is concurrent). The engine module ({@code analytics-query}) stays free of any
 * metrics-library dependency; only this wiring layer, which already depends on Micrometer via
 * Spring Boot's actuator starter, knows about it.
 */
final class MicrometerQueryMetrics implements QueryMetrics {

  private final MeterRegistry registry;
  private final Map<String, Timer> timers = new ConcurrentHashMap<>();

  MicrometerQueryMetrics(final MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void recordQueryDuration(final String datasetName, final long durationNanos) {
    timers
        .computeIfAbsent(
            datasetName,
            name ->
                Timer.builder("analytics.query.duration")
                    .description("Wall time of one dashboard read-path query")
                    .tag("dataset", name)
                    .register(registry))
        .record(durationNanos, TimeUnit.NANOSECONDS);
  }
}
