/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class AuthorizationCheckMetrics {

  private final Timer timer;

  public AuthorizationCheckMetrics(final MeterRegistry registry) {
    Objects.requireNonNull(registry, "MeterRegistry must not be null");
    final var meterDoc = AuthorizationMetricsDoc.CHECK_LATENCY;
    timer =
        Timer.builder(meterDoc.getName())
            .description(meterDoc.getDescription())
            .serviceLevelObjectives(meterDoc.getTimerSLOs())
            .register(registry);
  }

  public void record(final long durationNanos) {
    try {
      timer.record(durationNanos, TimeUnit.NANOSECONDS);
    } catch (final RuntimeException ignored) {
      // Metrics failures must never affect authorization decisions
    }
  }
}
