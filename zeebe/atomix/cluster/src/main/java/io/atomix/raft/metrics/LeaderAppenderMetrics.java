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

import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LeaderAppenderMetrics extends RaftMetrics implements CloseableSilently {
  private final MeterRegistry meterRegistry;
  private final Map<String, Timer> appendLatency;
  private final Map<String, Counter> appendDataRate;
  private final Map<String, Counter> appendRate;
  private final Counter commitRate;
  private final StatefulGauge nonCommittedEntriesValue;
  private final Map<String, StatefulGauge> nonReplicatedEntries;

  public LeaderAppenderMetrics(final String partitionName, final MeterRegistry meterRegistry) {
    super(partitionName);
    this.meterRegistry = meterRegistry;
    appendLatency = new HashMap<>();
    appendDataRate = new HashMap<>();
    appendRate = new HashMap<>();
    nonReplicatedEntries = new HashMap<>();

    commitRate =
        Counter.builder(LeaderMetricsDoc.COMMIT_RATE.getName())
            .description(LeaderMetricsDoc.COMMIT_RATE.getDescription())
            .tag(RaftKeyNames.PARTITION_GROUP.asString(), partitionGroupName)
            .register(meterRegistry);

    nonCommittedEntriesValue =
        StatefulGauge.builder(LeaderMetricsDoc.NON_COMMITTED_ENTRIES.getName())
            .description(LeaderMetricsDoc.NON_COMMITTED_ENTRIES.getDescription())
            .tag(RaftKeyNames.PARTITION_GROUP.asString(), partitionGroupName)
            .register(meterRegistry);
  }

  public void appendComplete(final long latencyms, final String memberId) {
    getAppendLatency(memberId).record(latencyms, TimeUnit.MILLISECONDS);
  }

  public void observeAppend(
      final String memberId, final int appendedEntries, final int appendedBytes) {
    getAppendRate(memberId).increment(appendedEntries);
    getAppendDataRate(memberId).increment(appendedBytes / 1024f);
  }

  public void observeCommit() {
    commitRate.increment();
  }

  public void observeNonCommittedEntries(final long remainingEntries) {
    nonCommittedEntriesValue.set(remainingEntries);
  }

  public void observeRemainingEntries(final String memberId, final long remainingEntries) {
    nonReplicatedEntries
        .computeIfAbsent(memberId, this::registerNonReplicatedEntries)
        .set(remainingEntries);
  }

  private Timer getAppendLatency(final String memberId) {
    return appendLatency.computeIfAbsent(
        memberId,
        id ->
            Timer.builder(LeaderMetricsDoc.APPEND_ENTRIES_LATENCY.getName())
                .description(LeaderMetricsDoc.APPEND_ENTRIES_LATENCY.getDescription())
                .serviceLevelObjectives(LeaderMetricsDoc.APPEND_ENTRIES_LATENCY.getTimerSLOs())
                .tags(
                    RaftKeyNames.FOLLOWER.asString(),
                    memberId,
                    RaftKeyNames.PARTITION_GROUP.asString(),
                    partitionGroupName)
                .register(meterRegistry));
  }

  private Counter getAppendDataRate(final String memberId) {
    return appendDataRate.computeIfAbsent(
        memberId,
        id ->
            Counter.builder(LeaderMetricsDoc.APPEND_DATA_RATE.getName())
                .description(LeaderMetricsDoc.APPEND_DATA_RATE.getDescription())
                .tags(
                    RaftKeyNames.FOLLOWER.asString(),
                    id,
                    RaftKeyNames.PARTITION_GROUP.asString(),
                    partitionGroupName)
                .register(meterRegistry));
  }

  private Counter getAppendRate(final String memberId) {
    return appendRate.computeIfAbsent(
        memberId,
        id ->
            Counter.builder(LeaderMetricsDoc.APPEND_RATE.getName())
                .description(LeaderMetricsDoc.APPEND_RATE.getDescription())
                .tags(
                    RaftKeyNames.FOLLOWER.asString(),
                    id,
                    RaftKeyNames.PARTITION_GROUP.asString(),
                    partitionGroupName)
                .register(meterRegistry));
  }

  private StatefulGauge registerNonReplicatedEntries(final String memberId) {
    return StatefulGauge.builder(LeaderMetricsDoc.NON_REPLICATED_ENTRIES.getName())
        .description(LeaderMetricsDoc.NON_REPLICATED_ENTRIES.getDescription())
        .tag(RaftKeyNames.FOLLOWER.asString(), memberId)
        .tag(RaftKeyNames.PARTITION_GROUP.asString(), partitionGroupName)
        .register(meterRegistry);
  }

  @Override
  public void close() {
    meterRegistry.remove(commitRate);
    meterRegistry.remove(nonCommittedEntriesValue);
    appendLatency.values().forEach(meterRegistry::remove);
    appendRate.values().forEach(meterRegistry::remove);
    appendDataRate.values().forEach(meterRegistry::remove);
    nonReplicatedEntries.values().forEach(meterRegistry::remove);
  }
}
