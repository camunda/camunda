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

import static io.atomix.raft.metrics.RaftServiceMetricsDoc.COMPACTION_TIME;

import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public final class RaftServiceMetrics extends RaftMetrics {

  private final Timer compactionTime;
  private final MeterRegistry registry;

  public RaftServiceMetrics(final String partitionName, final MeterRegistry registry) {
    super(partitionName);

    compactionTime =
        Timer.builder(COMPACTION_TIME.getName())
            .description(COMPACTION_TIME.getDescription())
            .serviceLevelObjectives(COMPACTION_TIME.getTimerSLOs())
            .tag(RaftKeyNames.PARTITION_GROUP.asString(), partitionGroupName)
            .register(registry);
    this.registry = registry;
  }

  public CloseableSilently compactionTime() {
    return MicrometerUtil.timer(compactionTime, Timer.start(registry.config().clock()));
  }
}
