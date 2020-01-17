/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.state;

import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

public final class SnapshotMetrics {
  private static final String NAMESPACE = "zeebe";
  private static final String PARTITION_LABEL_NAME = "partition";

  private static final Gauge SNAPSHOT_COUNT =
      Gauge.build()
          .namespace(NAMESPACE)
          .labelNames(PARTITION_LABEL_NAME)
          .name("snapshot_count")
          .help("Total count of committed snapshots on disk")
          .register();
  private static final Gauge SNAPSHOT_SIZE =
      Gauge.build()
          .namespace(NAMESPACE)
          .labelNames(PARTITION_LABEL_NAME)
          .name("snapshot_size_bytes")
          .help("Estimated snapshot size on disk")
          .register();
  private static final Gauge SNAPSHOT_DURATION =
      Gauge.build()
          .namespace(NAMESPACE)
          .labelNames(PARTITION_LABEL_NAME)
          .name("snapshot_duration_milliseconds")
          .help("Approximate duration of snapshot operation")
          .register();
  private static final Histogram SNAPSHOT_FILE_SIZE =
      Histogram.build()
          .namespace(NAMESPACE)
          .labelNames(PARTITION_LABEL_NAME)
          .help("Approximate size of snapshot files")
          .name("snapshot_file_size_megabytes")
          .buckets(.01, .1, .5, 1, 5, 10, 25, 50, 100, 250, 500)
          .register();

  private final String partitionId;
  private final SnapshotReplicationMetrics replication;

  public SnapshotMetrics(final int partitionId) {
    this.partitionId = String.valueOf(partitionId);
    this.replication = new SnapshotReplicationMetrics(this.partitionId);
  }

  public void incrementSnapshotCount() {
    SNAPSHOT_COUNT.labels(partitionId).inc();
  }

  public void decrementSnapshotCount() {
    SNAPSHOT_COUNT.labels(partitionId).dec();
  }

  public void setSnapshotCount(final int count) {
    SNAPSHOT_COUNT.labels(partitionId).set(count);
  }

  public void observeSnapshotSize(final long sizeInBytes) {
    SNAPSHOT_SIZE.labels(partitionId).set(sizeInBytes);
  }

  public void observeSnapshotFileSize(final long sizeInBytes) {
    SNAPSHOT_FILE_SIZE.labels(partitionId).observe(sizeInBytes / 1_000_000f);
  }

  public void observeSnapshotOperation(final long elapsedMillis) {
    SNAPSHOT_DURATION.labels(partitionId).set(elapsedMillis);
  }

  public SnapshotReplicationMetrics getReplication() {
    return replication;
  }
}
