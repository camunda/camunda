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

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ControlMessageResponse;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedEvent;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;

public class FailTaskTest
{
    private static final String TASK_TYPE = "foo";

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
    public void shouldFailTask()
    {
        // given
        client.createTask(TASK_TYPE);

        apiRule.openTaskSubscription(TASK_TYPE).await();

        final SubscribedEvent subscribedEvent = receiveSingleSubscribedEvent();

        // when
        final ExecuteCommandResponse response = client.failTask(subscribedEvent.key(), subscribedEvent.event());

        // then
        final Map<String, Object> expectedEvent = new HashMap<>(subscribedEvent.event());
        expectedEvent.put("state", "FAILED");

        assertThat(response.getEvent()).containsAllEntriesOf(expectedEvent);

        // and the task is published again
        final SubscribedEvent republishedEvent = receiveSingleSubscribedEvent();
        assertThat(republishedEvent.key()).isEqualTo(subscribedEvent.key());
        assertThat(republishedEvent.position()).isNotEqualTo(subscribedEvent.position());

        // and the task lifecycle is correct
        apiRule.openTopicSubscription("foo", 0).await();

        final int expectedTopicEvents = 8;

        final List<SubscribedEvent> taskEvents = doRepeatedly(() -> apiRule
                .moveMessageStreamToHead()
                .subscribedEvents()
                .filter(e -> e.subscriptionType() == SubscriptionType.TOPIC_SUBSCRIPTION)
                .limit(expectedTopicEvents)
                .collect(Collectors.toList()))
            .until(e -> e.size() == expectedTopicEvents);

        assertThat(taskEvents).extracting(e -> e.event().get("state"))
            .containsExactly(
                    "CREATE",
                    "CREATED",
                    "LOCK",
                    "LOCKED",
                    "FAIL",
                    "FAILED",
                    "LOCK",
                    "LOCKED");
    }

    @Test
    public void shouldRejectFailIfTaskNotFound()
    {
        // given
        final int key = 123;

        final Map<String, Object> event = new HashMap<>();
        event.put("type", "foo");

        // when
        final ExecuteCommandResponse response = client.failTask(key, event);

        // then
        assertThat(response.getEvent()).containsEntry("state", "FAIL_REJECTED");
    }

    @Test
    public void shouldRejectFailIfTaskAlreadyFailed()
    {
        // given
        client.createTask(TASK_TYPE);

        final ControlMessageResponse subscriptionResponse = apiRule.openTaskSubscription(TASK_TYPE).await();
        final int subscriberKey = (int) subscriptionResponse.getData().get("subscriberKey");

        final SubscribedEvent subscribedEvent = receiveSingleSubscribedEvent();
        apiRule.closeTaskSubscription(subscriberKey).await();

        client.failTask(subscribedEvent.key(), subscribedEvent.event());

        // when
        final ExecuteCommandResponse response = client.failTask(subscribedEvent.key(), subscribedEvent.event());

        // then
        assertThat(response.getEvent()).containsEntry("state", "FAIL_REJECTED");
    }


    @Test
    public void shouldRejectFailIfTaskCreated()
    {
        // given
        final ExecuteCommandResponse createResponse = client.createTask(TASK_TYPE);

        // when
        final ExecuteCommandResponse response = client.failTask(createResponse.key(), createResponse.getEvent());

        // then
        assertThat(response.getEvent()).containsEntry("state", "FAIL_REJECTED");
    }


    @Test
    public void shouldRejectFailIfTaskCompleted()
    {
        // given
        client.createTask(TASK_TYPE);

        apiRule.openTaskSubscription(TASK_TYPE).await();

        final SubscribedEvent subscribedEvent = receiveSingleSubscribedEvent();

        client.completeTask(subscribedEvent.key(), subscribedEvent.event());

        // when
        final ExecuteCommandResponse response = client.failTask(subscribedEvent.key(), subscribedEvent.event());

        // then
        assertThat(response.getEvent()).containsEntry("state", "FAIL_REJECTED");
    }

    @Test
    public void shouldRejectFailIfNotLockOwner()
    {
        // given
        final String lockOwner = "peter";

        client.createTask(TASK_TYPE);

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

        final SubscribedEvent subscribedEvent = receiveSingleSubscribedEvent();
        final Map<String, Object> event = subscribedEvent.event();
        event.put("lockOwner", "jan");

        // when
        final ExecuteCommandResponse response = client.failTask(subscribedEvent.key(), event);

        // then
        assertThat(response.getEvent()).containsEntry("state", "FAIL_REJECTED");
    }

    private SubscribedEvent receiveSingleSubscribedEvent()
    {
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 1);
        return apiRule.subscribedEvents().findFirst().get();
    }
}
