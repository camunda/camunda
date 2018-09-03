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

import static io.zeebe.protocol.Protocol.DEFAULT_TOPIC;
import static io.zeebe.test.util.TestUtil.doRepeatedly;

import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.api.clients.JobClient;
import io.zeebe.gateway.api.clients.TopicClient;
import io.zeebe.gateway.api.clients.WorkflowClient;
import io.zeebe.gateway.api.commands.BrokerInfo;
import io.zeebe.gateway.api.commands.Partition;
import io.zeebe.gateway.api.commands.PartitionInfo;
import io.zeebe.gateway.api.commands.Topic;
import io.zeebe.gateway.api.commands.Topology;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.rules.ExternalResource;

public class ClientRule extends ExternalResource {

  protected int defaultPartition;

  protected ZeebeClient client;

  protected final Properties properties;
  protected final EmbeddedBrokerRule brokerRule;

  public ClientRule(final EmbeddedBrokerRule brokerRule) {
    this(Properties::new, brokerRule);
  }

  public ClientRule(
      final Supplier<Properties> propertiesProvider, final EmbeddedBrokerRule brokerRule) {
    this.properties = propertiesProvider.get();
    this.brokerRule = brokerRule;
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
    client =
        ZeebeClient.newClientBuilder()
            .withProperties(properties)
            .brokerContactPoint(brokerRule.getClientAddress().toString())
            .build();
    createDefaultTopic();
  }

  private void createDefaultTopic() {

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
