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

import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.TaskIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;

public class TaskUpdateRetriesTest
{
    private static final String TASK_TYPE = "foo";
    private static final int NEW_RETRIES = 20;

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    private TestTopicClient client;

    @Before
    public void setup()
    {
        client = apiRule.topic();
    }

    @Test
    public void shouldUpdateRetries()
    {
        // given
        client.createTask(TASK_TYPE);

        apiRule.openTaskSubscription(TASK_TYPE).await();

        final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

        Map<String, Object> event = subscribedEvent.value();
        event.put("retries", 0);
        final ExecuteCommandResponse failResponse = client.failTask(subscribedEvent.key(), event);

        event = failResponse.getValue();
        event.put("retries", NEW_RETRIES);

        // when
        final ExecuteCommandResponse response = client.updateTaskRetries(subscribedEvent.key(), event);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(response.intent()).isEqualTo(TaskIntent.RETRIES_UPDATED);

        // and the task is published again
        final SubscribedRecord republishedEvent = receiveSingleSubscribedEvent();
        assertThat(republishedEvent.key()).isEqualTo(subscribedEvent.key());
        assertThat(republishedEvent.position()).isNotEqualTo(subscribedEvent.position());

        // and the task lifecycle is correct
        apiRule.openTopicSubscription("foo", 0).await();

        final int expectedTopicEvents = 10;

        final List<SubscribedRecord> taskEvents = doRepeatedly(() -> apiRule
                .moveMessageStreamToHead()
                .subscribedEvents()
                .filter(e -> e.subscriptionType() == SubscriptionType.TOPIC_SUBSCRIPTION &&
                      e.valueType() == ValueType.TASK)
                .limit(expectedTopicEvents)
                .collect(Collectors.toList()))
            .until(e -> e.size() == expectedTopicEvents);

        assertThat(taskEvents).extracting(e -> e.recordType(), e -> e.valueType(), e -> e.intent())
            .containsExactly(
                tuple(RecordType.COMMAND, ValueType.TASK, TaskIntent.CREATE),
                tuple(RecordType.EVENT, ValueType.TASK, TaskIntent.CREATED),
                tuple(RecordType.COMMAND, ValueType.TASK, TaskIntent.LOCK),
                tuple(RecordType.EVENT, ValueType.TASK, TaskIntent.LOCKED),
                tuple(RecordType.COMMAND, ValueType.TASK, TaskIntent.FAIL),
                tuple(RecordType.EVENT, ValueType.TASK, TaskIntent.FAILED),
                tuple(RecordType.COMMAND, ValueType.TASK, TaskIntent.UPDATE_RETRIES),
                tuple(RecordType.EVENT, ValueType.TASK, TaskIntent.RETRIES_UPDATED),
                tuple(RecordType.COMMAND, ValueType.TASK, TaskIntent.LOCK),
                tuple(RecordType.EVENT, ValueType.TASK, TaskIntent.LOCKED));
    }

    @Test
    public void shouldRejectUpdateRetriesIfTaskNotFound()
    {
        // given
        final Map<String, Object> event = new HashMap<>();

        event.put("retries", NEW_RETRIES);
        event.put("type", TASK_TYPE);

        // when
        final ExecuteCommandResponse response = client.updateTaskRetries(123, event);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.intent()).isEqualTo(TaskIntent.UPDATE_RETRIES);
    }

    @Test
    public void shouldRejectUpdateRetriesIfTaskCompleted()
    {
        // given
        client.createTask(TASK_TYPE);

        apiRule.openTaskSubscription(TASK_TYPE).await();

        final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

        Map<String, Object> event = subscribedEvent.value();
        final ExecuteCommandResponse completeResponse = client.completeTask(subscribedEvent.key(), event);

        event = completeResponse.getValue();
        event.put("retries", NEW_RETRIES);

        // when
        final ExecuteCommandResponse response = client.updateTaskRetries(subscribedEvent.key(), event);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.intent()).isEqualTo(TaskIntent.UPDATE_RETRIES);
    }

    @Test
    public void shouldRejectUpdateRetriesIfTaskLocked()
    {
        // given
        client.createTask(TASK_TYPE);

        apiRule.openTaskSubscription(TASK_TYPE).await();

        final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

        final Map<String, Object> event = subscribedEvent.value();
        event.put("retries", NEW_RETRIES);

        // when
        final ExecuteCommandResponse response = client.updateTaskRetries(subscribedEvent.key(), event);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.intent()).isEqualTo(TaskIntent.UPDATE_RETRIES);
    }


    @Test
    public void shouldRejectUpdateRetriesIfRetriesZero()
    {
        // given
        client.createTask(TASK_TYPE);

        apiRule.openTaskSubscription(TASK_TYPE).await();

        final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

        Map<String, Object> event = subscribedEvent.value();
        event.put("retries", 0);
        final ExecuteCommandResponse failResponse = client.failTask(subscribedEvent.key(), event);

        event = failResponse.getValue();
        event.put("retries", 0);

        // when
        final ExecuteCommandResponse response = client.updateTaskRetries(subscribedEvent.key(), event);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.intent()).isEqualTo(TaskIntent.UPDATE_RETRIES);
    }

    @Test
    public void shouldRejectUpdateRetriesIfRetriesLessThanZero()
    {
        // given
        client.createTask(TASK_TYPE);

        apiRule.openTaskSubscription(TASK_TYPE).await();

        final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

        Map<String, Object> event = subscribedEvent.value();
        event.put("retries", 0);
        final ExecuteCommandResponse failResponse = client.failTask(subscribedEvent.key(), event);

        event = failResponse.getValue();
        event.put("retries", -1);

        // when
        final ExecuteCommandResponse response = client.updateTaskRetries(subscribedEvent.key(), event);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.intent()).isEqualTo(TaskIntent.UPDATE_RETRIES);
    }

    private SubscribedRecord receiveSingleSubscribedEvent()
    {
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 1);
        return apiRule.subscribedEvents().findFirst().get();
    }
}
