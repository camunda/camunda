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

public class RaftRoleMetrics extends RaftMetrics {

  private static final Gauge ROLE =
      Gauge.build()
          .namespace("atomix")
          .name("role")
          .help("Shows current role")
          .labelNames("partitionGroupName", "partition")
          .register();

  private static final Counter HEARTBEAT_MISS =
      Counter.build()
          .namespace("atomix")
          .name("heartbeat_miss_count")
          .help("Count of missing heartbeats")
          .labelNames("partitionGroupName", "partition")
          .register();

  private static final Histogram HEARTBEAT_TIME =
      Histogram.build()
          .namespace("atomix")
          .name("heartbeat_time_in_s")
          .help("Time between heartbeats")
          .labelNames("partitionGroupName", "partition")
          .register();

  public RaftRoleMetrics(final String partitionName) {
    super(partitionName);
  }

  public void becomingFollower() {
    ROLE.labels(partitionGroupName, partition).set(1);
  }

  public void becomingCandidate() {
    ROLE.labels(partitionGroupName, partition).set(2);
  }

  public void becomingLeader() {
    ROLE.labels(partitionGroupName, partition).set(3);
  }

  public void countHeartbeatMiss() {
    HEARTBEAT_MISS.labels(partitionGroupName, partition).inc();
  }

  public void observeHeartbeatInterval(final long milliseconds) {
    HEARTBEAT_TIME.labels(partitionGroupName, partition).observe(milliseconds / 1000f);
  }

  public static double getHeartbeatMissCount(final String partition) {
    return HEARTBEAT_MISS.labels(partition, partition).get();
  }
}
