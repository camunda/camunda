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

import org.slf4j.LoggerFactory;

public class RaftMetrics {

  protected final String partition;
  protected final String partitionGroupName;

  RaftMetrics(final String partitionName) {
    int partitionId;
    String groupName;
    try {
      final String[] parts = partitionName.split("-");
      partitionId = Integer.parseInt(parts[parts.length - 1]);
      groupName = parts[0];
    } catch (final Exception e) {
      LoggerFactory.getLogger(RaftMetrics.class)
          .debug(
              "Cannot extract partition group name and id from {}, defaulting to raft and 0",
              partitionName);
      partitionId = 0;
      groupName = "raft";
    }
    this.partition = String.valueOf(partitionId);
    this.partitionGroupName = groupName;
  }
}
