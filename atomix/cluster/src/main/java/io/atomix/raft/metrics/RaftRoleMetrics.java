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
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Histogram.Timer;

public class RaftRoleMetrics extends RaftMetrics {

  private static final Gauge ROLE =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("role")
          .help("Shows current role")
          .labelNames(PARTITION_GROUP_NAME_LABEL, PARTITION_LABEL)
          .register();

  private static final Counter HEARTBEAT_MISS =
      Counter.build()
          .namespace(NAMESPACE)
          .name("heartbeat_miss_count")
          .help("Count of missing heartbeats")
          .labelNames(PARTITION_GROUP_NAME_LABEL, PARTITION_LABEL)
          .register();

  private static final Histogram HEARTBEAT_TIME =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("heartbeat_time_in_s")
          .help("Time between heartbeats")
          .labelNames(PARTITION_GROUP_NAME_LABEL, PARTITION_LABEL)
          .register();
  private static final Gauge ELECTION_LATENCY =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("election_latency_in_ms")
          .help("Duration for election")
          .labelNames(PARTITION_GROUP_NAME_LABEL, PARTITION_LABEL)
          .register();

  private static final Histogram LAST_FLUSHED_INDEX_UPDATE =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("last_flushed_index_update")
          .help("Time it takes to update the last flushed index")
          .labelNames(PARTITION_GROUP_NAME_LABEL, PARTITION_LABEL)
          .register();

  private final Gauge.Child role;
  private final Counter.Child heartbeatMiss;
  private final Histogram.Child heartbeatTime;
  private final Gauge.Child electionLatency;
  private final Histogram.Child lastFlushedIndexUpdate;

  public RaftRoleMetrics(final String partitionName) {
    super(partitionName);

    role = ROLE.labels(partitionGroupName, partition);
    heartbeatMiss = HEARTBEAT_MISS.labels(partitionGroupName, partition);
    heartbeatTime = HEARTBEAT_TIME.labels(partitionGroupName, partition);
    electionLatency = ELECTION_LATENCY.labels(partitionGroupName, partition);
    lastFlushedIndexUpdate = LAST_FLUSHED_INDEX_UPDATE.labels(partitionGroupName, partition);
  }

  public void becomingFollower() {
    role.set(1);
  }

  public void becomingCandidate() {
    role.set(2);
  }

  public void becomingLeader() {
    role.set(3);
  }

  public void countHeartbeatMiss() {
    heartbeatMiss.inc();
  }

  public void observeHeartbeatInterval(final long milliseconds) {
    heartbeatTime.observe(milliseconds / 1000f);
  }

  public static double getHeartbeatMissCount(final String partition) {
    return HEARTBEAT_MISS.labels(partition, partition).get();
  }

  public void setElectionLatency(final long latencyMs) {
    electionLatency.set(latencyMs);
  }

  public Timer observeLastFlushedIndexUpdate() {
    return lastFlushedIndexUpdate.startTimer();
  }
}
