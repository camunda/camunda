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

import static io.atomix.raft.metrics.RaftStartupMetricsDoc.*;

import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.MeterRegistry;

public class RaftStartupMetrics extends RaftMetrics {

  private final StatefulGauge bootstrapDuration;
  private final StatefulGauge joinDuration;

  public RaftStartupMetrics(final String partitionName, final MeterRegistry registry) {
    super(partitionName);

    bootstrapDuration =
        StatefulGauge.builder(BOOTSTRAP_DURATION.getName())
            .description(BOOTSTRAP_DURATION.getDescription())
            .tag(RaftKeyNames.PARTITION_GROUP.asString(), partitionGroupName)
            .register(registry);

    joinDuration =
        StatefulGauge.builder(JOIN_DURATION.getName())
            .description(JOIN_DURATION.getDescription())
            .tag(RaftKeyNames.PARTITION_GROUP.asString(), partitionGroupName)
            .register(registry);
  }

  public void observeBootstrapDuration(final long durationMillis) {
    bootstrapDuration.set(durationMillis);
  }

  public void observeJoinDuration(final long durationMillis) {
    joinDuration.set(durationMillis);
  }
}
