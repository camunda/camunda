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
import io.zeebe.gateway.api.commands.Partition;
import io.zeebe.gateway.api.commands.Topic;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PartitionManager {

  // local state
  private final Map<String, List<Integer>> topicsByName = new HashMap<>();

  private final ZeebeClient client;

  public PartitionManager(ZeebeClient client) {
    this.client = client;
  }

  // currently, this is a blocking request!
  // - but it's ok because it will be removed when Zeebe is a static system with only one topic
  public synchronized List<Integer> getPartitionIds(String topicName) {
    final List<Integer> partitions = topicsByName.get(topicName);
    if (partitions != null) {
      return partitions;
    }

    updateTopics();

    return topicsByName.get(topicName);
  }

  private void updateTopics() {
    client.newTopicsRequest().send().join().getTopics().forEach(this::addTopic);
  }

  private void addTopic(Topic topic) {
    final List<Integer> partitionIds =
        topic.getPartitions().stream().map(Partition::getId).collect(Collectors.toList());

    topicsByName.put(topic.getName(), partitionIds);
  }
}
