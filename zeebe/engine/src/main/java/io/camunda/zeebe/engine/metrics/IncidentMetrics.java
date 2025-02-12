/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.EngineKeyNames;
import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.IncidentAction;
import io.camunda.zeebe.util.micrometer.StatefulMeterRegistry;
import io.micrometer.core.instrument.Counter;
import java.util.concurrent.atomic.AtomicLong;

public final class IncidentMetrics {
  private final Counter incidentCreated;
  private final Counter incidentResolved;
  private final AtomicLong pendingIncidents;

  public IncidentMetrics(final StatefulMeterRegistry meterRegistry) {
    pendingIncidents = meterRegistry.newLongGauge(EngineMetricsDoc.PENDING_INCIDENTS).state();
    incidentCreated = registerCounter(meterRegistry, IncidentAction.CREATED);
    incidentResolved = registerCounter(meterRegistry, IncidentAction.RESOLVED);
  }

  public void incidentCreated() {
    incidentCreated.increment();
    pendingIncidents.incrementAndGet();
  }

  public void incidentResolved() {
    incidentResolved.increment();
    pendingIncidents.decrementAndGet();
  }

  private Counter registerCounter(
      final StatefulMeterRegistry meterRegistry, final IncidentAction action) {
    return Counter.builder(EngineMetricsDoc.INCIDENT_EVENTS.getName())
        .description(EngineMetricsDoc.INCIDENT_EVENTS.getDescription())
        .tag(EngineKeyNames.INCIDENT_ACTION.asString(), action.name())
        .register(meterRegistry);
  }
}
