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

import io.prometheus.client.Gauge;

public class RaftStartupMetrics extends RaftMetrics {
  private static final Gauge BOOTSTRAP_DURATION =
      Gauge.build()
          .namespace(NAMESPACE)
          .labelNames(PARTITION_GROUP_NAME_LABEL, PARTITION_LABEL)
          .help("Time taken to bootstrap the partition server (in ms)")
          .name("partition_server_bootstrap_time")
          .register();

  private static final Gauge JOIN_DURATION =
      Gauge.build()
          .namespace(NAMESPACE)
          .labelNames(PARTITION_GROUP_NAME_LABEL, PARTITION_LABEL)
          .help("Time taken for the partition server to join (in ms)")
          .name("partition_server_join_time")
          .register();

  private final Gauge.Child bootstrapDuration;
  private final Gauge.Child joinDuration;

  public RaftStartupMetrics(final String partitionName) {
    super(partitionName);
    bootstrapDuration = BOOTSTRAP_DURATION.labels(partitionGroupName, partition);
    joinDuration = JOIN_DURATION.labels(partitionGroupName, partition);
  }

  public void observeBootstrapDuration(final long durationMillis) {
    bootstrapDuration.set(durationMillis);
  }

  public void observeJoinDuration(final long durationMillis) {
    joinDuration.set(durationMillis);
  }
}
