/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import io.prometheus.client.Counter;
import io.prometheus.client.Counter.Child;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Histogram.Timer;

public final class SnapshotMetrics {
  private static final String NAMESPACE = "zeebe";
  private static final String PARTITION_LABEL_NAME = "partition";

  private static final Counter SNAPSHOT_COUNT =
      Counter.build()
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
  private static final Gauge SNAPSHOT_CHUNK_COUNT =
      Gauge.build()
          .namespace(NAMESPACE)
          .labelNames(PARTITION_LABEL_NAME)
          .name("snapshot_chunks_count")
          .help("Number of chunks in the last snapshot")
          .register();
  private static final Histogram SNAPSHOT_DURATION =
      Histogram.build()
          .namespace(NAMESPACE)
          .labelNames(PARTITION_LABEL_NAME)
          .name("snapshot_duration")
          .help("Approximate duration of snapshot operation")
          .register();

  private static final Histogram SNAPSHOT_PERSIST_DURATION =
      Histogram.build()
          .namespace(NAMESPACE)
          .labelNames(PARTITION_LABEL_NAME)
          .name("snapshot_persist_duration")
          .help("Approximate duration of snapshot persist operation")
          .register();
  private static final Histogram SNAPSHOT_FILE_SIZE =
      Histogram.build()
          .namespace(NAMESPACE)
          .labelNames(PARTITION_LABEL_NAME)
          .help("Approximate size of snapshot files")
          .name("snapshot_file_size_megabytes")
          .buckets(.01, .1, .5, 1, 5, 10, 25, 50, 100, 250, 500)
          .register();

  private final Histogram.Child snapshotPersistDuration;
  private final Histogram.Child snapshotFileSize;
  private final Histogram.Child snapshotDuration;
  private final Gauge.Child snapshotChunkCount;
  private final Gauge.Child snapshotSize;
  private final Child snapshotCount;

  public SnapshotMetrics(final String partitionId) {
    snapshotDuration = SNAPSHOT_DURATION.labels(partitionId);
    snapshotPersistDuration = SNAPSHOT_PERSIST_DURATION.labels(partitionId);
    snapshotFileSize = SNAPSHOT_FILE_SIZE.labels(partitionId);
    snapshotChunkCount = SNAPSHOT_CHUNK_COUNT.labels(partitionId);
    snapshotSize = SNAPSHOT_SIZE.labels(partitionId);
    snapshotCount = SNAPSHOT_COUNT.labels(partitionId);
  }

  void incrementSnapshotCount() {
    snapshotCount.inc();
  }

  void observeSnapshotSize(final long sizeInBytes) {
    snapshotSize.set(sizeInBytes);
  }

  void observeSnapshotChunkCount(final long count) {
    snapshotChunkCount.set(count);
  }

  void observeSnapshotFileSize(final long sizeInBytes) {
    snapshotFileSize.observe(sizeInBytes / 1_000_000f);
  }

  Timer startTimer() {
    return snapshotDuration.startTimer();
  }

  Timer startPersistTimer() {
    return snapshotPersistDuration.startTimer();
  }
}
