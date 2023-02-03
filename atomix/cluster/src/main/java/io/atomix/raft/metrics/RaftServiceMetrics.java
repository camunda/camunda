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

import io.prometheus.client.Histogram;

public class RaftServiceMetrics extends RaftMetrics {

  private static final Histogram COMPACTION_TIME =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("compaction_time_ms")
          .help("Time spend to compact")
          .labelNames(PARTITION_GROUP_NAME_LABEL, PARTITION_LABEL)
          .register();

  private final Histogram.Child compactionTime;

  public RaftServiceMetrics(final String partitionName) {
    super(partitionName);

    compactionTime = COMPACTION_TIME.labels(partitionGroupName, partition);
  }

  public void compactionTime(final long latencyms) {
    compactionTime.observe(latencyms / 1000f);
  }
}
