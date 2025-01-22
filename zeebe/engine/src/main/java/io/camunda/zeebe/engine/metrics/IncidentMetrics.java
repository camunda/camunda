/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.micrometer.core.instrument.Metrics;
import java.util.concurrent.atomic.AtomicLong;

public final class IncidentMetrics {
  static final io.micrometer.core.instrument.Counter.Builder INCIDENT_EVENTS_BUILDER =
      io.micrometer.core.instrument.Counter.builder("zeebe_incident_events_total")
          .description("Number of incident events");
  private static final io.micrometer.core.instrument.MeterRegistry METER_REGISTRY =
      Metrics.globalRegistry;
  private static final AtomicLong PENDING_INCIDENTS = new AtomicLong(0);

  private final String partitionIdLabel;

  public IncidentMetrics(final int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
    io.micrometer.core.instrument.Gauge.builder(
            "zeebe_pending_incidents_total", PENDING_INCIDENTS, AtomicLong::get)
        .description("Number of pending incidents")
        .tags("partition", partitionIdLabel)
        .register(METER_REGISTRY);
  }

  private void incidentEvent(final String action) {
    INCIDENT_EVENTS_BUILDER
        .tags("action", action, "partition", partitionIdLabel)
        .register(METER_REGISTRY)
        .increment();
  }

  public void incidentCreated() {
    incidentEvent("created");
    PENDING_INCIDENTS.incrementAndGet();
  }

  public void incidentResolved() {
    incidentEvent("resolved");
    PENDING_INCIDENTS.decrementAndGet();
  }
}
