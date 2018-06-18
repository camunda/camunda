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
package io.zeebe.test;

import static io.zeebe.test.util.TestUtil.doRepeatedly;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.clients.TopicClient;
import io.zeebe.client.api.clients.WorkflowClient;
import io.zeebe.client.api.commands.BrokerInfo;
import io.zeebe.client.api.commands.Partition;
import io.zeebe.client.api.commands.PartitionInfo;
import io.zeebe.client.api.commands.Topic;
import io.zeebe.client.api.commands.Topology;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.rules.ExternalResource;

public class ClientRule extends ExternalResource {
  public static final String DEFAULT_TOPIC = "default-topic";
  protected int defaultPartition;

  protected ZeebeClient client;
  protected final boolean createDefaultTopic;

  protected final Properties properties;

  public ClientRule() {
    this(true);
  }

  public ClientRule(final boolean createDefaultTopic) {
    this(Properties::new, createDefaultTopic);
  }

  public ClientRule(
      final Supplier<Properties> propertiesProvider, final boolean createDefaultTopic) {
    this.properties = propertiesProvider.get();
    this.createDefaultTopic = createDefaultTopic;
  }

  public ZeebeClient getClient() {
    return client;
  }

  public TopicClient topicClient() {
    return client.topicClient();
  }

  public JobClient jobClient() {
    return client.topicClient().jobClient();
  }

  public WorkflowClient workflowClient() {
    return client.topicClient().workflowClient();
  }

  public String getDefaultTopic() {
    return DEFAULT_TOPIC;
  }

  public int getDefaultPartition() {
    return defaultPartition;
  }

  @Override
  protected void before() {
    client = ZeebeClient.newClientBuilder().withProperties(properties).build();

    if (createDefaultTopic) {
      createDefaultTopic();
    }
  }

  private void createDefaultTopic() {
    client
        .newCreateTopicCommand()
        .name(DEFAULT_TOPIC)
        .partitions(1)
        .replicationFactor(1)
        .send()
        .join();
    waitUntilTopicsExists(DEFAULT_TOPIC);

    final Topology topology = client.newTopologyRequest().send().join();

    defaultPartition = -1;
    final List<BrokerInfo> topologyBrokers = topology.getBrokers();

    for (BrokerInfo leader : topologyBrokers) {
      final List<PartitionInfo> partitions = leader.getPartitions();
      for (PartitionInfo brokerPartitionState : partitions) {
        if (DEFAULT_TOPIC.equals(brokerPartitionState.getTopicName())
            && brokerPartitionState.isLeader()) {
          defaultPartition = brokerPartitionState.getPartitionId();
          break;
        }
      }
    }

    if (defaultPartition < 0) {
      throw new RuntimeException("Could not detect leader for default partition");
    }
  }

  public void waitUntilTopicsExists(final String... topicNames) {
    final List<String> expectedTopicNames = Arrays.asList(topicNames);

    doRepeatedly(this::topicsByName).until(t -> t.keySet().containsAll(expectedTopicNames));
  }

  public Map<String, List<Partition>> topicsByName() {
    return client
        .newTopicsRequest()
        .send()
        .join()
        .getTopics()
        .stream()
        .collect(Collectors.toMap(Topic::getName, Topic::getPartitions));
  }

  @Override
  protected void after() {
    client.close();
    client = null;
  }
}
