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
package io.zeebe.broker.task;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.Intent;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;

public class CompleteTaskTest
{
    private static final String TASK_TYPE = "foo";

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    @Test
    public void shouldCompleteTask()
    {
        // given
        createTask(TASK_TYPE);

        apiRule.openTaskSubscription(TASK_TYPE).await();

        final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

        // when
        final ExecuteCommandResponse response = completeTask(subscribedEvent.key(), subscribedEvent.value());

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(response.intent()).isEqualTo(Intent.COMPLETED);
    }

    @Test
    public void shouldRejectCompletionIfTaskNotFound()
    {
        // given
        final int key = 123;

        final Map<String, Object> event = new HashMap<>();
        event.put("type", "foo");

        // when
        final ExecuteCommandResponse response = completeTask(key, event);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.intent()).isEqualTo(Intent.COMPLETE);
    }

    @Test
    public void shouldRejectCompletionIfPayloadIsInvalid()
    {
        // given
        createTask(TASK_TYPE);

        apiRule.openTaskSubscription(TASK_TYPE).await();

        final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

        final Map<String, Object> event = subscribedEvent.value();
        event.put("payload", new byte[] {1}); // positive fixnum, i.e. no object

        // when
        final ExecuteCommandResponse response = completeTask(subscribedEvent.key(), event);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.intent()).isEqualTo(Intent.COMPLETE);
    }

    @Test
    public void shouldRejectCompletionIfTaskIsCompleted()
    {
        // given
        final ExecuteCommandResponse response2 = createTask(TASK_TYPE);
        assertThat(response2.recordType()).isEqualTo(RecordType.EVENT);

        apiRule.openTaskSubscription(TASK_TYPE).await();

        final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();
        completeTask(subscribedEvent.key(), subscribedEvent.value());

        // when
        final ExecuteCommandResponse response = completeTask(subscribedEvent.key(), subscribedEvent.value());

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.intent()).isEqualTo(Intent.COMPLETE);
    }

    @Test
    public void shouldRejectCompletionIfTaskNotLocked()
    {
        // given
        final ExecuteCommandResponse task = createTask(TASK_TYPE);

        // when
        final ExecuteCommandResponse response = completeTask(task.key(), task.getValue());

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.intent()).isEqualTo(Intent.COMPLETE);
    }

    @Test
    public void shouldRejectCompletionIfNotLockOwner()
    {
        // given
        final String lockOwner = "kermit";

        createTask(TASK_TYPE);

        apiRule.createControlMessageRequest()
            .partitionId(apiRule.getDefaultPartitionId())
            .messageType(ControlMessageType.ADD_TASK_SUBSCRIPTION)
            .data()
                .put("taskType", TASK_TYPE)
                .put("lockDuration", Duration.ofSeconds(30).toMillis())
                .put("lockOwner", lockOwner)
                .put("credits", 10)
                .done()
            .sendAndAwait();

        final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();
        final Map<String, Object> event = subscribedEvent.value();
        event.put("lockOwner", "ms piggy");

        // when
        final ExecuteCommandResponse response = completeTask(subscribedEvent.key(), event);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.intent()).isEqualTo(Intent.COMPLETE);
    }


    private ExecuteCommandResponse createTask(String type)
    {
        return apiRule.createCmdRequest()
            .type(ValueType.TASK, Intent.CREATE)
            .command()
                .put("type", type)
                .put("retries", 3)
            .done()
            .sendAndAwait();
    }

    private ExecuteCommandResponse completeTask(long key, Map<String, Object> event)
    {
        return apiRule.createCmdRequest()
            .type(ValueType.TASK, Intent.COMPLETE)
            .key(key)
            .command()
                .putAll(event)
            .done()
            .sendAndAwait();
    }

    private SubscribedRecord receiveSingleSubscribedEvent()
    {
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 1);
        return apiRule.subscribedEvents().findFirst().get();
    }
}
