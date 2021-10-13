/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.prometheus.client.Gauge;

public class RoleMetrics {
  private static final Gauge LEADER_TRANSITION_LATENCY =
      Gauge.build()
          .namespace("zeebe")
          .name("leader_transition_latency")
          .help(
              "The time (in ms) needed for the engine services to transition to leader and be ready to process new requests.")
          .labelNames("partition")
          .register();

  private final String partitionIdLabel;

  public RoleMetrics(final int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
  }

  public Gauge.Timer startLeaderTransitionLatencyTimer() {
    return LEADER_TRANSITION_LATENCY.labels(partitionIdLabel).startTimer();
  }
}
