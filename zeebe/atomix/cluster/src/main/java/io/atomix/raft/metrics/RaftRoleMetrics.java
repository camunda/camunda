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

import static io.atomix.raft.metrics.RaftRoleMetricsDoc.*;

import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

public class RaftRoleMetrics extends RaftMetrics {

  private final Counter heartbeatMiss;
  private final Timer heartbeatTime;
  private final StatefulGauge role;
  private final StatefulGauge electionLatency;

  public RaftRoleMetrics(final String partitionName, final MeterRegistry registry) {
    super(partitionName);

    heartbeatMiss =
        Counter.builder(HEARTBEAT_MISS.getName())
            .description(HEARTBEAT_MISS.getDescription())
            .tag(RaftKeyNames.PARTITION_GROUP.asString(), partitionGroupName)
            .register(registry);
    heartbeatTime =
        Timer.builder(HEARTBEAT_TIME.getName())
            .description(HEARTBEAT_TIME.getDescription())
            .serviceLevelObjectives(HEARTBEAT_TIME.getTimerSLOs())
            .tag(RaftKeyNames.PARTITION_GROUP.asString(), partitionGroupName)
            .register(registry);

    role =
        StatefulGauge.builder(ROLE.getName())
            .description(ROLE.getDescription())
            .tag(RaftKeyNames.PARTITION_GROUP.asString(), partitionGroupName)
            .register(registry);

    electionLatency =
        StatefulGauge.builder(ELECTION_LATENCY.getName())
            .description(ELECTION_LATENCY.getDescription())
            .tag(RaftKeyNames.PARTITION_GROUP.asString(), partitionName)
            .register(registry);
  }

  public void becomingInactive() {
    role.set(0);
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
    heartbeatMiss.increment();
  }

  public void observeHeartbeatInterval(final long milliseconds) {
    heartbeatTime.record(milliseconds, TimeUnit.MILLISECONDS);
  }

  public double getHeartbeatMissCount() {
    return heartbeatMiss.count();
  }

  public void setElectionLatency(final long latencyMs) {
    electionLatency.set(latencyMs);
  }
}
