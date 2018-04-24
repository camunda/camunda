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
package io.zeebe.client.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

import io.zeebe.client.TasksClient;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.event.impl.TaskEventImpl;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.util.ClientRule;
import io.zeebe.client.util.Events;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;

public class CompleteTaskTest
{

    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule brokerRule = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected TasksClient client;

    protected final MsgPackConverter converter = new MsgPackConverter();

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient().tasks();
    }

    @Test
    public void shouldCompleteTask()
    {
        // given
        final TaskEventImpl baseEvent = Events.exampleTask();

        brokerRule.onExecuteCommandRequest(EventType.TASK_EVENT, "COMPLETE")
            .respondWith()
            .key(123)
            .value()
              .allOf((r) -> r.getCommand())
              .put("state", "COMPLETED")
              .done()
            .register();

        final String updatedPayload = "{\"fruit\":\"cherry\"}";

        // when
        final TaskEvent taskEvent = clientRule.tasks()
            .complete(baseEvent)
            .payload(updatedPayload)
            .execute();

        // then
        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(request.eventType()).isEqualTo(EventType.TASK_EVENT);
        assertThat(request.partitionId()).isEqualTo(StubBrokerRule.TEST_PARTITION_ID);
        assertThat(request.position()).isEqualTo(baseEvent.getMetadata().getPosition());

        assertThat(request.getCommand()).containsOnly(
                entry("state", "COMPLETE"),
                entry("lockTime", baseEvent.getLockExpirationTime().toEpochMilli()),
                entry("lockOwner", baseEvent.getLockOwner()),
                entry("retries", baseEvent.getRetries()),
                entry("type", baseEvent.getType()),
                entry("headers", baseEvent.getHeaders()),
                entry("customHeaders", baseEvent.getCustomHeaders()),
                entry("payload", converter.convertToMsgPack(updatedPayload)));

        assertThat(taskEvent.getMetadata().getKey()).isEqualTo(123L);
        assertThat(taskEvent.getMetadata().getTopicName()).isEqualTo(StubBrokerRule.TEST_TOPIC_NAME);
        assertThat(taskEvent.getMetadata().getPartitionId()).isEqualTo(StubBrokerRule.TEST_PARTITION_ID);

        assertThat(taskEvent.getState()).isEqualTo("COMPLETED");
        assertThat(taskEvent.getHeaders()).isEqualTo(baseEvent.getHeaders());
        assertThat(taskEvent.getLockExpirationTime()).isEqualTo(baseEvent.getLockExpirationTime());
        assertThat(taskEvent.getLockOwner()).isEqualTo(baseEvent.getLockOwner());
        assertThat(taskEvent.getRetries()).isEqualTo(baseEvent.getRetries());
        assertThat(taskEvent.getType()).isEqualTo(baseEvent.getType());
        assertThat(taskEvent.getPayload()).isEqualTo(updatedPayload);
    }

    @Test
    public void shouldClearPayload()
    {
        // given
        final TaskEventImpl baseEvent = Events.exampleTask();

        brokerRule.onExecuteCommandRequest(EventType.TASK_EVENT, "COMPLETE")
            .respondWith()
            .key(123)
            .value()
              .allOf((r) -> r.getCommand())
              .put("state", "COMPLETED")
              .done()
            .register();


        // when
        clientRule.tasks()
            .complete(baseEvent)
            .withoutPayload()
            .execute();

        // then
        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);

        assertThat(request.getCommand()).doesNotContainKey("payload");
    }

    @Test
    public void shouldThrowExceptionOnRejection()
    {
        // given
        final TaskEventImpl baseEvent = Events.exampleTask();

        brokerRule.onExecuteCommandRequest(EventType.TASK_EVENT, "COMPLETE")
            .respondWith()
            .key(r -> r.key())
            .value()
              .allOf((r) -> r.getCommand())
              .put("state", "COMPLETE_REJECTED")
              .done()
            .register();

        final String updatedPayload = "{\"fruit\":\"cherry\"}";

        // then
        exception.expect(ClientCommandRejectedException.class);
        exception.expectMessage("Command for event with key 79 was rejected by broker (COMPLETE_REJECTED)");

        // when
        clientRule.tasks()
            .complete(baseEvent)
            .payload(updatedPayload)
            .execute();
    }

    @Test
    public void shouldThrowExceptionIfBaseEventIsNull()
    {
        // given
        final String updatedPayload = "{\"fruit\":\"cherry\"}";

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("base event must not be null");

        // when
        clientRule.tasks()
            .complete(null)
            .payload(updatedPayload)
            .execute();
    }

    @Test
    public void shouldSetPayloadAsStream()
    {
        // given
        final TaskEventImpl baseEvent = Events.exampleTask();

        brokerRule.onExecuteCommandRequest(EventType.TASK_EVENT, "COMPLETE")
            .respondWith()
            .key(123)
            .value()
              .allOf((r) -> r.getCommand())
              .put("state", "COMPLETED")
              .done()
            .register();

        final String updatedPayload = "{\"fruit\":\"cherry\"}";
        final ByteArrayInputStream inStream =
                new ByteArrayInputStream(updatedPayload.getBytes(StandardCharsets.UTF_8));

        // when
        clientRule.tasks()
            .complete(baseEvent)
            .payload(inStream)
            .execute();

        // then
        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);

        assertThat(request.getCommand()).contains(
                entry("payload", converter.convertToMsgPack(updatedPayload)));
    }

}
