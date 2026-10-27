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

import static io.atomix.raft.metrics.RaftReplicationMetricsDoc.*;

import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.MeterRegistry;

public class RaftReplicationMetrics extends RaftMetrics {

  private final StatefulGauge commitIndex;
  private final StatefulGauge appendIndex;

  public RaftReplicationMetrics(final String partitionName, final MeterRegistry registry) {
    super(partitionName);

    commitIndex =
        StatefulGauge.builder(COMMIT_INDEX.getName())
            .description(COMMIT_INDEX.getDescription())
            .tag(RaftKeyNames.PARTITION_GROUP.asString(), partitionGroupName)
            .register(registry);
    appendIndex =
        StatefulGauge.builder(APPEND_INDEX.getName())
            .description(APPEND_INDEX.getDescription())
            .tag(RaftKeyNames.PARTITION_GROUP.asString(), partitionGroupName)
            .register(registry);
  }

  public void setCommitIndex(final long value) {
    commitIndex.set(value);
  }

  public void setAppendIndex(final long value) {
    appendIndex.set(value);
  }
}
