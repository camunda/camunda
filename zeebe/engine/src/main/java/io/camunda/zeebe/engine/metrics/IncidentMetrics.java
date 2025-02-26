/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static io.camunda.zeebe.engine.metrics.EngineMetricsDoc.PENDING_INCIDENTS;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.EngineKeyNames;
import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.IncidentAction;
import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public final class IncidentMetrics {
  private final Counter incidentCreated;
  private final Counter incidentResolved;
  private final StatefulGauge pendingIncidents;

  public IncidentMetrics(final MeterRegistry meterRegistry) {
    pendingIncidents =
        StatefulGauge.builder(PENDING_INCIDENTS.getName())
            .description(PENDING_INCIDENTS.getDescription())
            .register(meterRegistry);
    incidentCreated = registerCounter(meterRegistry, IncidentAction.CREATED);
    incidentResolved = registerCounter(meterRegistry, IncidentAction.RESOLVED);
  }

  public void incidentCreated() {
    incidentCreated.increment();
    pendingIncidents.increment();
  }

  public void incidentResolved() {
    incidentResolved.increment();
    pendingIncidents.decrement();
  }

  private Counter registerCounter(final MeterRegistry meterRegistry, final IncidentAction action) {
    return Counter.builder(EngineMetricsDoc.INCIDENT_EVENTS.getName())
        .description(EngineMetricsDoc.INCIDENT_EVENTS.getDescription())
        .tag(EngineKeyNames.INCIDENT_ACTION.asString(), action.toString())
        .register(meterRegistry);
  }
}
