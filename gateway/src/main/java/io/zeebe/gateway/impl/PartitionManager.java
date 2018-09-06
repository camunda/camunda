/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.zeebe.gateway.impl;

import io.zeebe.gateway.ZeebeClient;
import java.util.ArrayList;
import java.util.List;

public class PartitionManager {

  // local state
  private final List<Integer> partitions = new ArrayList<>();

  private final ZeebeClient client;

  public PartitionManager(final ZeebeClient client) {
    this.client = client;
  }

  // currently, this is a blocking request!
  // - but it's ok because it will be removed when Zeebe is a static system with only one topic
  public synchronized List<Integer> getPartitionIds() {
    final List<Integer> partitions = this.partitions;
    if (partitions != null && !partitions.isEmpty()) {
      return partitions;
    }

    updatePartitions();

    return this.partitions;
  }

  private void updatePartitions() {
    client
        .newPartitionsRequest()
        .send()
        .join()
        .getPartitions()
        .stream()
        .map(p -> p.getId())
        .forEach(partitions::add);
  }
}
