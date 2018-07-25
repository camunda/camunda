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
package io.zeebe.client.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.*;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import java.util.*;
import org.assertj.core.api.Condition;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class RequestTopologyTest {

  public ClientRule clientRule = new ClientRule();
  public StubBrokerRule brokerRule = new StubBrokerRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Test
  public void shouldRequestTopics() {
    // given
    final Map<String, Object> broker1 = buildBroker("0.0.0.0", 51015);
    broker1.put(
        "partitions",
        Arrays.asList(
            buildPartition(0, "system", "LEADER"), buildPartition(1, "my-topic", "FOLLOWER")));

    final Map<String, Object> broker2 = buildBroker("0.0.0.0", 41015);
    broker2.put(
        "partitions",
        Arrays.asList(
            buildPartition(0, "system", "FOLLOWER"), buildPartition(1, "my-topic", "LEADER")));

    final List<Map<String, Object>> brokers = Arrays.asList(broker1, broker2);

    brokerRule
        .onControlMessageRequest(r -> r.messageType() == ControlMessageType.REQUEST_TOPOLOGY)
        .respondWith()
        .data()
        .put("brokers", brokers)
        .done()
        .register();

    final ZeebeClient client = clientRule.getClient();

    // when
    final Topology topology = client.newTopologyRequest().send().join();

    // then
    final List<BrokerInfo> returnedBrokers = topology.getBrokers();
    assertThat(returnedBrokers).hasSize(2);
    assertThat(returnedBrokers)
        .extracting(BrokerInfo::getAddress)
        .contains("0.0.0.0:51015", "0.0.0.0:41015");

    assertThat(returnedBrokers.get(0).getPartitions())
        .hasSize(2)
        .areExactly(1, partition(0, "system", PartitionBrokerRole.LEADER))
        .areExactly(1, partition(1, "my-topic", PartitionBrokerRole.FOLLOWER));

    assertThat(returnedBrokers.get(1).getPartitions())
        .hasSize(2)
        .areExactly(1, partition(0, "system", PartitionBrokerRole.FOLLOWER))
        .areExactly(1, partition(1, "my-topic", PartitionBrokerRole.LEADER));
  }

  public Map<String, Object> buildBroker(String host, int port) {
    final Map<String, Object> broker = new HashMap<>();
    broker.put("host", host);
    broker.put("port", port);

    return broker;
  }

  public Map<String, Object> buildPartition(int id, String topic, String role) {
    final Map<String, Object> partition = new HashMap<>();
    partition.put("topicName", topic);
    partition.put("partitionId", id);
    partition.put("role", role);

    return partition;
  }

  private Condition<PartitionInfo> partition(int id, String topic, PartitionBrokerRole role) {
    return new Condition<>(
        p -> p.getPartitionId() == id && p.getTopicName().equals(topic) && p.getRole() == role, "");
  }
}
