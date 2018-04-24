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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

import io.zeebe.client.TasksClient;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.event.impl.TaskEventImpl;
import io.zeebe.client.util.ClientRule;
import io.zeebe.client.util.Events;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;

public class FailTaskTest
{

    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule brokerRule = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected TasksClient client;


    @Test
    public void shouldFailTask()
    {
        // given
        final TaskEventImpl baseEvent = Events.exampleTask();

        brokerRule.onExecuteCommandRequest(EventType.TASK_EVENT, "FAIL")
            .respondWith()
            .key(123)
            .value()
              .allOf((r) -> r.getCommand())
              .put("state", "FAILED")
              .done()
            .register();

        // when
        final TaskEvent taskEvent = clientRule.tasks()
            .fail(baseEvent)
            .retries(4)
            .execute();

        // then
        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(request.eventType()).isEqualTo(EventType.TASK_EVENT);
        assertThat(request.partitionId()).isEqualTo(StubBrokerRule.TEST_PARTITION_ID);

        assertThat(request.getCommand()).containsOnly(
                entry("state", "FAIL"),
                entry("lockTime", baseEvent.getLockExpirationTime().toEpochMilli()),
                entry("lockOwner", baseEvent.getLockOwner()),
                entry("retries", 4),
                entry("type", baseEvent.getType()),
                entry("headers", baseEvent.getHeaders()),
                entry("customHeaders", baseEvent.getCustomHeaders()),
                entry("payload", baseEvent.getPayloadMsgPack()));

        assertThat(taskEvent.getMetadata().getKey()).isEqualTo(123L);
        assertThat(taskEvent.getMetadata().getTopicName()).isEqualTo(StubBrokerRule.TEST_TOPIC_NAME);
        assertThat(taskEvent.getMetadata().getPartitionId()).isEqualTo(StubBrokerRule.TEST_PARTITION_ID);

        assertThat(taskEvent.getState()).isEqualTo("FAILED");
        assertThat(taskEvent.getHeaders()).isEqualTo(baseEvent.getHeaders());
        assertThat(taskEvent.getLockExpirationTime()).isEqualTo(baseEvent.getLockExpirationTime());
        assertThat(taskEvent.getLockOwner()).isEqualTo(baseEvent.getLockOwner());
        assertThat(taskEvent.getType()).isEqualTo(baseEvent.getType());
        assertThat(taskEvent.getPayload()).isEqualTo(baseEvent.getPayload());
        assertThat(taskEvent.getRetries()).isEqualTo(4);
    }

    @Test
    public void shouldThrowExceptionOnRejection()
    {
        // given
        final TaskEventImpl baseEvent = Events.exampleTask();

        brokerRule.onExecuteCommandRequest(EventType.TASK_EVENT, "FAIL")
            .respondWith()
            .key((r) -> r.key())
            .value()
              .allOf((r) -> r.getCommand())
              .put("state", "FAIL_REJECTED")
              .done()
            .register();

        // then
        exception.expect(ClientCommandRejectedException.class);
        exception.expectMessage("Command for event with key 79 was rejected by broker (FAIL_REJECTED)");

        // when
        clientRule.tasks()
            .fail(baseEvent)
            .retries(5)
            .execute();
    }

    @Test
    public void shouldThrowExceptionIfBaseEventIsNull()
    {
        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("base event must not be null");

        // when
        clientRule.tasks()
            .fail(null)
            .retries(5)
            .execute();
    }

}
