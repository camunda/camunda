/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.prometheus.client.Gauge;

public final class BannedInstanceMetrics {

  private static final Gauge BANNED_INSTANCES_COUNTER =
      Gauge.build()
          .namespace("zeebe")
          .name("banned_instances_total")
          .help("Number of banned instances")
          .labelNames("partition")
          .register();

  private final String partitionIdLabel;

  public BannedInstanceMetrics(final int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
  }

  public void countBannedInstance() {
    BANNED_INSTANCES_COUNTER.labels(partitionIdLabel).inc();
  }

  public void setBannedInstanceCounter(final int counter) {
    BANNED_INSTANCES_COUNTER.labels(partitionIdLabel).set(counter);
  }
}
