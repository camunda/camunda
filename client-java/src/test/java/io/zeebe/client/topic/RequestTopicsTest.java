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
package io.zeebe.client.topic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.assertj.core.api.Condition;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.Partition;
import io.zeebe.client.api.commands.Topic;
import io.zeebe.client.api.commands.Topics;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.test.broker.protocol.brokerapi.ControlMessageRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;

public class RequestTopicsTest
{

    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule brokerRule = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

    @Test
    public void shouldRequestTopics()
    {
        // given
        final List<Map<String, Object>> partitions = new ArrayList<>();
        partitions.add(buildPartition(1, "foo"));
        partitions.add(buildPartition(2, "foo"));
        partitions.add(buildPartition(3, "bar"));

        brokerRule.onControlMessageRequest(r -> r.messageType() == ControlMessageType.REQUEST_PARTITIONS && r.partitionId() == Protocol.SYSTEM_PARTITION)
            .respondWith()
            .data()
                .put("partitions", partitions)
                .done()
            .register();

        final ZeebeClient client = clientRule.getClient();

        // when
        final Topics result = client.newTopicsRequest()
                .send()
                .join();

        // then
        final List<Topic> returnedTopics = result.getTopics();
        assertThat(returnedTopics).hasSize(2);

        final Map<String, List<Partition>> topicsByName = returnedTopics.stream().collect(Collectors.toMap(Topic::getName, Topic::getPartitions));

        assertThat(topicsByName.get("foo"))
            .hasSize(2)
            .areExactly(1, matching(1, "foo"))
            .areExactly(1, matching(2, "foo"));

        assertThat(topicsByName.get("bar"))
            .hasSize(1)
            .areExactly(1, matching(3, "bar"));

        final List<ControlMessageRequest> partitionRequests = brokerRule.getReceivedControlMessageRequests().stream()
            .filter(r -> r.messageType() == ControlMessageType.REQUEST_PARTITIONS)
            .collect(Collectors.toList());

        assertThat(partitionRequests).hasSize(1);
        final ControlMessageRequest request = partitionRequests.get(0);
        assertThat(request.partitionId()).isEqualTo(Protocol.SYSTEM_PARTITION);
    }

    protected Condition<Partition> matching(int id, String topic)
    {
        final Predicate<Partition> predicate = p -> p.getId() == id && topic.equals(p.getTopicName());
        return new Condition<>(predicate, "foo");
    }

    public Map<String, Object> buildPartition(int id, String topic)
    {
        final Map<String, Object> partition = new HashMap<>();
        partition.put("topic", topic);
        partition.put("id", id);

        return partition;
    }

}
