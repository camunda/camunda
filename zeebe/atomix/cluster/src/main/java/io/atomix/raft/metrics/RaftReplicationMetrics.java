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

public class RaftReplicationMetrics extends RaftMetrics {

  private static final Gauge COMMIT_INDEX =
      Gauge.build()
          .namespace(NAMESPACE)
          .labelNames(PARTITION_GROUP_NAME_LABEL, PARTITION_LABEL)
          .help("The commit index")
          .name("partition_raft_commit_index")
          .register();

  private static final Gauge APPEND_INDEX =
      Gauge.build()
          .namespace(NAMESPACE)
          .labelNames(PARTITION_GROUP_NAME_LABEL, PARTITION_LABEL)
          .help("The index of last entry appended to the log")
          .name("partition_raft_append_index")
          .register();

  private final Gauge.Child commitIndex;
  private final Gauge.Child appendIndex;

  public RaftReplicationMetrics(final String partitionName) {
    super(partitionName);

    commitIndex = COMMIT_INDEX.labels(partitionGroupName, partition);
    appendIndex = APPEND_INDEX.labels(partitionGroupName, partition);
  }

  public void setCommitIndex(final long value) {
    commitIndex.set(value);
  }

  public void setAppendIndex(final long value) {
    appendIndex.set(value);
  }
}
