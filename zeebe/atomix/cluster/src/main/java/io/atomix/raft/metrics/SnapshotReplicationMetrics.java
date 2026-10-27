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

import static io.atomix.raft.metrics.SnapshotReplicationMetricsDoc.*;

import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.MeterRegistry;

public class SnapshotReplicationMetrics extends RaftMetrics implements CloseableSilently {

  private final MeterRegistry meterRegistry;
  private final StatefulGauge count;
  private final StatefulGauge duration;

  public SnapshotReplicationMetrics(final String partitionName, final MeterRegistry meterRegistry) {
    super(partitionName);
    this.meterRegistry = meterRegistry;

    count =
        StatefulGauge.builder(COUNT.getName())
            .description(COUNT.getDescription())
            .tag(RaftKeyNames.PARTITION_GROUP.asString(), partitionGroupName)
            .register(meterRegistry);

    duration =
        StatefulGauge.builder(DURATION.getName())
            .description(DURATION.getDescription())
            .tag(RaftKeyNames.PARTITION_GROUP.asString(), partitionGroupName)
            .register(meterRegistry);
  }

  public void incrementCount() {
    count.increment();
  }

  public void decrementCount() {
    count.decrement();
  }

  public void setCount(final int value) {
    count.set(value);
  }

  public void observeDuration(final long durationMillis) {
    duration.set(durationMillis);
  }

  @Override
  public void close() {
    meterRegistry.remove(count);
    meterRegistry.remove(duration);
  }
}
