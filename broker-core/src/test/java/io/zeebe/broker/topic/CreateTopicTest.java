/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.topic;

import static io.zeebe.test.broker.protocol.brokerapi.data.BrokerPartitionState.LEADER_STATE;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.agrona.DirectBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.broker.clustering.api.CreatePartitionRequest;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.Intent;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ControlMessageResponse;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferUtil;

public class CreateTopicTest
{
    protected static final SocketAddress BROKER_MGMT_ADDRESS = new SocketAddress("localhost", 51016);
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    @Test
    public void shouldCreateTopic()
    {
        // given
        final String topicName = "newTopic";

        // when
        final ExecuteCommandResponse response = apiRule.createTopic(topicName, 2);

        // then
        assertThat(response.getValue())
            .containsExactly(
                entry("name", topicName),
                entry("partitions", 2),
                entry("replicationFactor", 1),
                entry("partitionIds", Collections.EMPTY_LIST)
            );

        assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(response.intent()).isEqualTo(Intent.CREATING);
    }

    @Test
    public void shouldNotCreateSystemTopic()
    {
        // when
        final ExecuteCommandResponse response = apiRule.createTopic(Protocol.SYSTEM_TOPIC, 2);

        // then
        assertThat(response.getValue())
            .containsExactly(
                entry("name", Protocol.SYSTEM_TOPIC),
                entry("partitions", 2),
                entry("replicationFactor", 1),
                entry("partitionIds", Collections.EMPTY_LIST)
            );

        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.intent()).isEqualTo(Intent.CREATE);
    }

    @Test
    public void shouldNotCreateExistingTopic() throws InterruptedException
    {
        // given
        final String topicName = "newTopic";
        apiRule.createTopic(topicName, 2);

        // when
        final ExecuteCommandResponse response = apiRule.createTopic(topicName, 2);

        // then
        assertThat(response.getValue())
            .containsExactly(
                entry("name", topicName),
                entry("partitions", 2),
                entry("replicationFactor", 1),
                entry("partitionIds", Collections.EMPTY_LIST)
            );

        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.intent()).isEqualTo(Intent.CREATE);
    }

    @Test
    public void shouldNotCreateTopicWithZeroPartitions()
    {
        // given
        final String topicName = "newTopic";
        final int numberOfPartitions = 0;

        // when
        final ExecuteCommandResponse response = apiRule.createTopic(topicName, numberOfPartitions);

        // then
        assertThat(response.getValue())
            .containsExactly(
                entry("name", topicName),
                entry("partitions", numberOfPartitions),
                entry("replicationFactor", 1),
                entry("partitionIds", Collections.EMPTY_LIST)
            );

        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.intent()).isEqualTo(Intent.CREATE);
    }

    @Test
    public void shouldNotCreateTopicWithNegativePartitions()
    {
        // given
        final String topicName = "newTopic";
        final int numberOfPartitions = -100;

        // when
        final ExecuteCommandResponse response = apiRule.createTopic(topicName, numberOfPartitions);

        // then
        assertThat(response.getValue())
            .containsExactly(
                entry("name", topicName),
                entry("partitions", numberOfPartitions),
                entry("replicationFactor", 1),
                entry("partitionIds", Collections.EMPTY_LIST)
            );
    }

    @Test
    public void shouldNotCreateTopicWithZeroReplicationFactor()
    {
        // given
        final String topicName = "newTopic";

        // when
        final ExecuteCommandResponse response = apiRule.createTopic(topicName, 1, 0);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.intent()).isEqualTo(Intent.CREATE);

        assertThat(response.getValue())
            .containsExactly(
                entry("name", topicName),
                entry("partitions", 1),
                entry("replicationFactor", 0),
                entry("partitionIds", Collections.EMPTY_LIST)
            );
    }

    @Test
    public void shouldNotCreateTopicWithNegativeReplicationFactor()
    {
        // given
        final String topicName = "newTopic";

        // when
        final ExecuteCommandResponse response = apiRule.createTopic(topicName, 1, -1);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.intent()).isEqualTo(Intent.CREATE);

        assertThat(response.getValue())
            .containsExactly(
                entry("name", topicName),
                entry("partitions", 1),
                entry("replicationFactor", -1),
                entry("partitionIds", Collections.EMPTY_LIST)
            );

        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.intent()).isEqualTo(Intent.CREATE);
    }

    @Test
    public void shouldCreateTopicAfterRejection()
    {

        // given a rejected creation request
        final String topicName = "newTopic";
        apiRule.createTopic(topicName, 0);

        // when I send a valid creation request for the same topic
        final ExecuteCommandResponse response = apiRule.createTopic(topicName, 1);

        // then this is successful
        assertThat(response.getValue())
            .containsExactly(
                entry("name", topicName),
                entry("partitions", 1),
                entry("replicationFactor", 1),
                entry("partitionIds", Collections.EMPTY_LIST)
            );

        assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(response.intent()).isEqualTo(Intent.CREATING);
    }

    @Test
    public void shouldRejectPartitionCreationAndNotBreak()
    {
        // given
        final ClientTransport transport = apiRule.getTransport();
        final RemoteAddress remoteAddress = transport.registerRemoteAndAwaitChannel(BROKER_MGMT_ADDRESS);
        final ClientOutput output = transport.getOutput();

        final CreatePartitionRequest partitionMessage = new CreatePartitionRequest();

        final DirectBuffer topicName = BufferUtil.wrapString("foo");
        final int partition1 = 142;
        final int partition2 = 143;

        partitionMessage.topicName(topicName);
        partitionMessage.partitionId(partition1);
        partitionMessage.replicationFactor(1);

        doRepeatedly(() -> output.sendRequest(remoteAddress, partitionMessage)).until(r -> r != null); // => should create partition
        doRepeatedly(() -> output.sendRequest(remoteAddress, partitionMessage)).until(r -> r != null); // => should be rejected/ignored

        // when creating another partition
        partitionMessage.partitionId(partition2);
        doRepeatedly(() -> output.sendRequest(remoteAddress, partitionMessage)).until(r -> r != null);

        // then this should be successful (i.e. the rejected request should not have jammed the broker)
        waitUntil(() -> arePublished(partition1, partition2));
    }

    protected boolean arePublished(int... partitions)
    {
        final ControlMessageResponse response = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.REQUEST_TOPOLOGY)
            .sendAndAwait();

        final Set<Integer> expectedPartitions = Arrays.stream(partitions).boxed().collect(Collectors.toSet());

        final Map<String, Object> topology = response.getData();
        final List<Map<String, Object>> brokers = (List<Map<String, Object>>) topology.get("brokers");

        for (Map<String, Object> broker : brokers)
        {
            final List<Map<String, Object>> brokerPartitionStates = (List<Map<String, Object>>) broker.get("partitions");
            for (Map<String, Object> brokerPartitionState : brokerPartitionStates)
            {
                final String state = brokerPartitionState.get("state").toString();
                if (state.equals(LEADER_STATE))
                {
                    expectedPartitions.remove(brokerPartitionState.get("partitionId"));
                }
            }
        }

        return expectedPartitions.isEmpty();
    }
}
