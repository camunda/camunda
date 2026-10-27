/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.adapter;

import io.camunda.security.core.port.out.AuthorizationCheckLatencyRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NullMarked;

/**
 * Micrometer-backed {@link AuthorizationCheckLatencyRecorder}, engine-local since {@code core}'s
 * {@link io.camunda.security.core.authz.AuthorizationPortsFactory} is called directly by {@link
 * io.camunda.zeebe.engine.processing.EngineProcessors} with no Spring container available. Builds
 * its {@link Timer} from the port's name, description, and SLO-bucket constants, matching {@code
 * spring-boot-starter}'s Spring adapter of the same port. {@code METRIC_BASE_UNIT} is excepted —
 * {@code Timer.Builder} has no {@code baseUnit(...)} setter, so this meter does not carry it. See
 * camunda-security-library's ADR-0041.
 */
@NullMarked
public final class MicrometerAuthorizationCheckLatencyRecorder
    implements AuthorizationCheckLatencyRecorder {

  private final Timer timer;

  public MicrometerAuthorizationCheckLatencyRecorder(final MeterRegistry meterRegistry) {
    timer =
        Timer.builder(METRIC_NAME)
            .description(METRIC_DESCRIPTION)
            .serviceLevelObjectives(METRIC_SLO_BUCKETS.toArray(new Duration[0]))
            .register(meterRegistry);
  }

  @Override
  public void record(final long durationNanos) {
    timer.record(durationNanos, TimeUnit.NANOSECONDS);
  }
}
