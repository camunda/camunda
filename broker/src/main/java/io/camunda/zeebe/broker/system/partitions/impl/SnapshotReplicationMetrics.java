/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.partitions.impl;

import io.prometheus.client.Gauge;

/** Snapshot replication metrics to be used on the consumer side */
public class SnapshotReplicationMetrics {
  private static final String NAMESPACE = "zeebe";
  private static final String PARTITION_LABEL_NAME = "partition";

  private static final Gauge COUNT =
      Gauge.build()
          .namespace(NAMESPACE)
          .labelNames(PARTITION_LABEL_NAME)
          .help("Count of ongoing snapshot replication")
          .name("snapshot_replication_count")
          .register();
  private static final Gauge DURATION =
      Gauge.build()
          .namespace(NAMESPACE)
          .labelNames(PARTITION_LABEL_NAME)
          .help("Approximate duration of replication in milliseconds")
          .name("snapshot_replication_duration_milliseconds")
          .register();

  private final String partitionId;

  public SnapshotReplicationMetrics(final String partitionId) {
    this.partitionId = partitionId;
  }

  public void incrementCount() {
    COUNT.labels(partitionId).inc();
  }

  public void decrementCount() {
    COUNT.labels(partitionId).dec();
  }

  public void setCount(final long value) {
    COUNT.labels(partitionId).set(value);
  }

  public void observeDuration(final long durationMillis) {
    DURATION.labels(partitionId).set(durationMillis);
  }
}
