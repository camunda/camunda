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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;

public class RaftStartupMetrics extends RaftMetrics {

  private final AtomicLong bootstrapDuration;
  private final AtomicLong joinDuration;

  public RaftStartupMetrics(final String partitionName, final MeterRegistry registry) {
    super(partitionName);
    bootstrapDuration = new AtomicLong(0L);
    joinDuration = new AtomicLong(0L);

    Gauge.builder(BOOTSTRAP_DURATION.getName(), bootstrapDuration::get)
        .description(BOOTSTRAP_DURATION.getDescription())
        .tags(RaftKeyNames.PARTITION_GROUP.asString(), partitionGroupName)
        .register(registry);

    Gauge.builder(JOIN_DURATION.getName(), joinDuration::get)
        .description(JOIN_DURATION.getDescription())
        .tags(RaftKeyNames.PARTITION_GROUP.asString(), partitionGroupName)
        .register(registry);
  }

  public void observeBootstrapDuration(final long durationMillis) {
    bootstrapDuration.set(durationMillis);
  }

  public void observeJoinDuration(final long durationMillis) {
    joinDuration.set(durationMillis);
  }
}
