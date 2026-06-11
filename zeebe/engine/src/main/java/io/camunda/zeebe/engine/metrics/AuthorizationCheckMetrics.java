/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.engine.metrics.AuthorizationMetricsDoc.AuthorizationKeyNames;
import io.camunda.zeebe.engine.metrics.AuthorizationMetricsDoc.AuthorizationOutcome;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class AuthorizationCheckMetrics {

  private final MeterRegistry registry;
  private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();

  public AuthorizationCheckMetrics(final MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "MeterRegistry must not be null");
  }

  public void record(
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final AuthorizationOutcome outcome,
      final long durationNanos) {
    try {
      final var key = resourceType.name() + ":" + permissionType.name() + ":" + outcome.name();
      timers
          .computeIfAbsent(key, k -> buildTimer(resourceType, permissionType, outcome))
          .record(durationNanos, TimeUnit.NANOSECONDS);
    } catch (final RuntimeException ignored) {
      // Metrics failures must never affect authorization decisions
    }
  }

  private Timer buildTimer(
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final AuthorizationOutcome outcome) {
    final var meterDoc = AuthorizationMetricsDoc.CHECK_LATENCY;
    return Timer.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .serviceLevelObjectives(meterDoc.getTimerSLOs())
        .tag(AuthorizationKeyNames.RESOURCE_TYPE.asString(), resourceType.name())
        .tag(AuthorizationKeyNames.PERMISSION_TYPE.asString(), permissionType.name())
        .tag(AuthorizationKeyNames.OUTCOME.asString(), outcome.getLabel())
        .register(registry);
  }
}
