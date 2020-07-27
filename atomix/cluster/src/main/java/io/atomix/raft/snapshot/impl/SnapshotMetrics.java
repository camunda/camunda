/*
 * Copyright © 2020  camunda services GmbH (info@camunda.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package io.atomix.raft.snapshot.impl;

import io.prometheus.client.Counter;
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
  private static final Histogram SNAPSHOT_FILE_SIZE =
      Histogram.build()
          .namespace(NAMESPACE)
          .labelNames(PARTITION_LABEL_NAME)
          .help("Approximate size of snapshot files")
          .name("snapshot_file_size_megabytes")
          .buckets(.01, .1, .5, 1, 5, 10, 25, 50, 100, 250, 500)
          .register();

  private final String partitionId;

  public SnapshotMetrics(final String partitionName) {
    this.partitionId = partitionName;
  }

  void incrementSnapshotCount() {
    SNAPSHOT_COUNT.labels(partitionId).inc();
  }

  void observeSnapshotSize(final long sizeInBytes) {
    SNAPSHOT_SIZE.labels(partitionId).set(sizeInBytes);
  }

  void observeSnapshotChunkCount(final long count) {
    SNAPSHOT_CHUNK_COUNT.labels(partitionId).set(count);
  }

  void observeSnapshotFileSize(final long sizeInBytes) {
    SNAPSHOT_FILE_SIZE.labels(partitionId).observe(sizeInBytes / 1_000_000f);
  }

  Timer startTimer() {
    return SNAPSHOT_DURATION.labels(partitionId).startTimer();
  }
}
