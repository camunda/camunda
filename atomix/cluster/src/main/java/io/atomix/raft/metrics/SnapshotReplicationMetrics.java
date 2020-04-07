/*
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

import io.prometheus.client.Gauge;

public class SnapshotReplicationMetrics extends RaftMetrics {
  private static final String NAMESPACE = "atomix";
  private static final String PARTITION_GROUP_NAME_LABEL = "partitionGroupName";
  private static final String PARTITION_LABEL = "partition";

  private static final Gauge COUNT =
      Gauge.build()
          .namespace(NAMESPACE)
          .labelNames(PARTITION_GROUP_NAME_LABEL, PARTITION_LABEL)
          .help("Count of ongoing snapshot replication")
          .name("snapshot_replication_count")
          .register();
  private static final Gauge DURATION =
      Gauge.build()
          .namespace(NAMESPACE)
          .labelNames(PARTITION_GROUP_NAME_LABEL, PARTITION_LABEL)
          .help("Approximate duration of replication in milliseconds")
          .name("snapshot_replication_duration_milliseconds")
          .register();

  public SnapshotReplicationMetrics(final String partitionName) {
    super(partitionName);
  }

  public void incrementCount() {
    COUNT.labels(partitionGroupName, partition).inc();
  }

  public void decrementCount() {
    COUNT.labels(partitionGroupName, partition).dec();
  }

  public void setCount(final int value) {
    COUNT.labels(partitionGroupName, partition).set(value);
  }

  public void observeDuration(final long durationMillis) {
    DURATION.labels(partitionGroupName, partition).set(durationMillis);
  }
}
