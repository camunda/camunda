/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

public final class IncidentMetrics {

  private static final Counter INCIDENT_EVENTS =
      Counter.build()
          .namespace("zeebe")
          .name("incident_events_total")
          .help("Number of incident events")
          .labelNames("action", "partition")
          .register();

  private static final Gauge PENDING_INCIDENTS =
      Gauge.build()
          .namespace("zeebe")
          .name("pending_incidents_total")
          .help("Number of pending incidents")
          .labelNames("partition")
          .register();

  private final String partitionIdLabel;

  public IncidentMetrics(final int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
  }

  private void incidentEvent(final String action) {
    INCIDENT_EVENTS.labels(action, partitionIdLabel).inc();
  }

  public void incidentCreated() {
    incidentEvent("created");
    PENDING_INCIDENTS.labels(partitionIdLabel).inc();
  }

  public void incidentResolved() {
    incidentEvent("resolved");
    PENDING_INCIDENTS.labels(partitionIdLabel).dec();
  }
}
