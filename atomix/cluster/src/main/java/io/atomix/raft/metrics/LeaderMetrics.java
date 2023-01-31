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

import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;

public class LeaderMetrics extends RaftMetrics {
  private static final String FOLLOWER_LABEL = "follower";

  private static final Histogram APPEND_LATENCY =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("append_entries_latency")
          .help("Latency to append an entry to a follower")
          .labelNames(FOLLOWER_LABEL, PARTITION_GROUP_NAME_LABEL, PARTITION_LABEL)
          .register();

  private static final Counter APPEND_RATE =
      Counter.build()
          .namespace(NAMESPACE)
          .name("append_entries_rate")
          .help("The rate of entries appended (counting entries, not their size)")
          .labelNames(FOLLOWER_LABEL, PARTITION_GROUP_NAME_LABEL, PARTITION_LABEL)
          .register();

  private static final Counter APPEND_DATA_RATE =
      Counter.build()
          .namespace(NAMESPACE)
          .name("append_entries_data_rate")
          .help("The rate of data replication in KiB")
          .labelNames(FOLLOWER_LABEL, PARTITION_GROUP_NAME_LABEL, PARTITION_LABEL)
          .register();

  private static final Counter COMMIT_RATE =
      Counter.build()
          .namespace(NAMESPACE)
          .name("commit_entries_rate")
          .help("The rate of entries committed (counting entries, not their size)")
          .labelNames(PARTITION_GROUP_NAME_LABEL, PARTITION_LABEL)
          .register();

  private final Counter.Child commitRate;

  public LeaderMetrics(final String partitionName) {
    super(partitionName);
    commitRate = COMMIT_RATE.labels(partitionGroupName, partition);
  }

  public void appendComplete(final long latencyms, final String memberId) {
    APPEND_LATENCY.labels(memberId, partitionGroupName, partition).observe(latencyms / 1000f);
  }

  public void observeAppend(
      final String memberId, final int appendedEntries, final int appendedBytes) {
    APPEND_RATE.labels(memberId, partitionGroupName, partition).inc(appendedEntries);
    APPEND_DATA_RATE.labels(memberId, partitionGroupName, partition).inc(appendedBytes / 1024f);
  }

  public void observeCommit() {
    commitRate.inc();
  }
}
