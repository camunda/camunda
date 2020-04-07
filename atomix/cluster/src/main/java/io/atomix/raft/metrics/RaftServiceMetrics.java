/*
 * Copyright 2016-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.metrics;

import io.prometheus.client.Histogram;

public class RaftServiceMetrics extends RaftMetrics {

  private static final Histogram SNAPSHOTING_TIME =
      Histogram.build()
          .namespace("atomix")
          .name("snapshot_time_ms")
          .help("Time spend to take a snapshot")
          .labelNames("partitionGroupName", "partition")
          .register();

  private static final Histogram COMPACTION_TIME =
      Histogram.build()
          .namespace("atomix")
          .name("compaction_time_ms")
          .help("Time spend to compact")
          .labelNames("partitionGroupName", "partition")
          .register();

  public RaftServiceMetrics(final String partitionName) {
    super(partitionName);
  }

  public void snapshotTime(final long latencyms) {
    // Historgram class expect seconds not milliseconds, for that we need to divied by 1000
    SNAPSHOTING_TIME.labels(partitionGroupName, partition).observe(latencyms / 1000f);
  }

  public void compactionTime(final long latencyms) {
    COMPACTION_TIME.labels(partitionGroupName, partition).observe(latencyms / 1000f);
  }
}
