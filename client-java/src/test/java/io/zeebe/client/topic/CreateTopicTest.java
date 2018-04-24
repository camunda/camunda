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
import static org.assertj.core.api.Assertions.entry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.Event;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;

public class CreateTopicTest
{

    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule brokerRule = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected ZeebeClient client;

    @Test
    public void shouldCreateTopic()
    {
        // given
        brokerRule.onExecuteCommandRequest(Protocol.SYSTEM_PARTITION, EventType.TOPIC_EVENT, "CREATE")
            .respondWith()
            .key(123)
            .position(456)
            .value()
              .allOf(ExecuteCommandRequest::getCommand)
              .put("state", "CREATING")
              .done()
            .register();

        // when
        final Event responseEvent = clientRule.topics().create("newTopic", 14).execute();

        // then
        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(request.eventType()).isEqualTo(EventType.TOPIC_EVENT);
        assertThat(request.partitionId()).isEqualTo(Protocol.SYSTEM_PARTITION);
        assertThat(request.position()).isEqualTo(ExecuteCommandRequestEncoder.positionNullValue());

        assertThat(request.getCommand()).containsOnly(
                entry("state", "CREATE"),
                entry("name", "newTopic"),
                entry("partitions", 14),
                entry("replicationFactor", 1));

        assertThat(responseEvent.getMetadata().getKey()).isEqualTo(123L);
        assertThat(responseEvent.getMetadata().getTopicName()).isEqualTo(Protocol.SYSTEM_TOPIC);
        assertThat(responseEvent.getMetadata().getPartitionId()).isEqualTo(Protocol.SYSTEM_PARTITION);
        assertThat(responseEvent.getMetadata().getPosition()).isEqualTo(456);

        assertThat(responseEvent.getState()).isEqualTo("CREATING");
    }

    @Test
    public void shouldValidateTopicNameNotNull()
    {
        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("name must not be null");

        // when
        clientRule.topics()
            .create(null, 3)
            .execute();
    }
}
