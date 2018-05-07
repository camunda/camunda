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

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.broker.task.processor.TaskSubscription;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.TaskIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ControlMessageRequestBuilder;
import io.zeebe.test.broker.protocol.clientapi.ControlMessageResponse;
import io.zeebe.test.broker.protocol.clientapi.ErrorResponse;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.StringUtil;


public class TaskSubscriptionTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    private TestTopicClient testClient;

    @Before
    public void setUp()
    {
        testClient = apiRule.topic();
    }

    @Test
    public void shouldAddTaskSubscription() throws InterruptedException
    {
        // given
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TASK_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("taskType", "foo")
                .put("lockDuration", 10000L)
                .put("lockOwner", "bar")
                .put("credits", 5)
                .done()
            .send();

        // when
        final ExecuteCommandResponse response = testClient.createTask("foo");

        // then
        final SubscribedRecord taskEvent = testClient.receiveEvents()
                .ofTypeTask()
                .withIntent(TaskIntent.LOCKED)
                .getFirst();
        assertThat(taskEvent.key()).isEqualTo(response.key());
        assertThat(taskEvent.position()).isGreaterThan(response.position());
        assertThat(taskEvent.value())
            .containsEntry("type", "foo")
            .containsEntry("retries", 3)
            .containsEntry("lockOwner", "bar");

        final List<Intent> taskStates = testClient
            .receiveRecords()
            .ofTypeTask()
            .limit(4)
            .map(e -> e.intent())
            .collect(Collectors.toList());

        assertThat(taskStates).containsExactly(TaskIntent.CREATE, TaskIntent.CREATED, TaskIntent.LOCK, TaskIntent.LOCKED);
    }

    @Test
    public void shouldRemoveTaskSubscription()
    {
        // given
        final ControlMessageResponse openResponse = apiRule.openTaskSubscription("foo").await();
        final int subscriberKey = (int) openResponse.getData().get("subscriberKey");

        // when
        final ControlMessageResponse closeResponse = apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TASK_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("subscriberKey", subscriberKey)
                .done()
            .send()
            .await();

        assertThat(closeResponse.getData()).containsEntry("subscriberKey", subscriberKey);
    }

    @Test
    public void shouldNoLongerLockTasksAfterRemoval() throws InterruptedException
    {
        // given
        final String taskType = "foo";
        final ControlMessageResponse openResponse = apiRule.openTaskSubscription(taskType).await();
        final int subscriberKey = (int) openResponse.getData().get("subscriberKey");

        apiRule.closeTaskSubscription(subscriberKey).await();

        // when
        testClient.createTask(taskType);

        // then
        apiRule.openTopicSubscription("test", 0).await();

        Thread.sleep(500L);

        final int eventsAvailable = apiRule.numSubscribedEventsAvailable();
        final List<SubscribedRecord> receivedEvents = apiRule.subscribedEvents().limit(eventsAvailable).collect(Collectors.toList());

        assertThat(receivedEvents).hasSize(2);
        assertThat(receivedEvents).allMatch(e -> e.subscriptionType() == SubscriptionType.TOPIC_SUBSCRIPTION);
        assertThat(receivedEvents).extracting(r -> r.intent())
            .containsExactly(TaskIntent.CREATE, TaskIntent.CREATED); // no more LOCK etc.
    }

    @Test
    public void shouldRejectSubscriptionWithZeroCredits()
    {
        // given
        final ControlMessageRequestBuilder request = apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TASK_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("taskType", "foo")
                .put("lockDuration", 10000L)
                .put("lockOwner", "bar")
                .put("credits", 0)
                .done();
        // when
        final ErrorResponse errorResponse = request
            .send()
            .awaitError();

        // then
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorResponse.getErrorData()).isEqualTo("Cannot add task subscription. subscription credits must be greater than 0");
    }

    @Test
    public void shouldRejectSubscriptionWithNegativeCredits()
    {
        // given
        final ControlMessageRequestBuilder request = apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TASK_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("taskType", "foo")
                .put("lockDuration", 10000L)
                .put("lockOwner", "bar")
                .put("credits", -1)
                .done();
        // when
        final ErrorResponse errorResponse = request
            .send()
            .awaitError();

        // then
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorResponse.getErrorData()).isEqualTo("Cannot add task subscription. subscription credits must be greater than 0");
    }

    @Test
    public void shouldRejectSubscriptionWithoutLockOwner()
    {
        // given
        final ControlMessageRequestBuilder request = apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TASK_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("taskType", "foo")
                .put("lockDuration", 10000L)
                .put("credits", 5)
                .done();
        // when
        final ErrorResponse errorResponse = request
            .send()
            .awaitError();

        // then
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorResponse.getErrorData()).isEqualTo("Cannot add task subscription. lock owner must not be empty");
    }

    @Test
    public void shouldRejectSubscriptionWithExcessiveLockOwnerName()
    {
        // given
        final String lockOwner = StringUtil.stringOfLength(TaskSubscription.LOCK_OWNER_MAX_LENGTH + 1);

        final ControlMessageRequestBuilder request = apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TASK_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("taskType", "foo")
                .put("lockDuration", 10000L)
                .put("lockOwner", lockOwner)
                .put("credits", 5)
                .done();
        // when
        final ErrorResponse errorResponse = request
            .send()
            .awaitError();

        // then
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorResponse.getErrorData()).isEqualTo("Cannot add task subscription. length of lock owner must be less than or equal to 64");
    }

    @Test
    public void shouldRejectSubscriptionWithZeroLockDuration()
    {
        // given
        final ControlMessageRequestBuilder request = apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TASK_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("taskType", "foo")
                .put("lockDuration", 0)
                .put("lockOwner", "bar")
                .put("credits", 5)
                .done();
        // when
        final ErrorResponse errorResponse = request
            .send()
            .awaitError();

        // then
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorResponse.getErrorData()).isEqualTo("Cannot add task subscription. lock duration must be greater than 0");
    }

    @Test
    public void shouldRejectSubscriptionWithNegativeLockDuration()
    {
        // given
        final ControlMessageRequestBuilder request = apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TASK_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("taskType", "foo")
                .put("lockDuration", -1)
                .put("lockOwner", "bar")
                .put("credits", 5)
                .done();
        // when
        final ErrorResponse errorResponse = request
            .send()
            .awaitError();

        // then
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorResponse.getErrorData()).isEqualTo("Cannot add task subscription. lock duration must be greater than 0");
    }

    @Test
    public void shouldDistributeTasksInRoundRobinFashion()
    {
        // given
        final String taskType = "foo";
        final int subscriber1 = (int) apiRule
                .openTaskSubscription(taskType)
                .await()
                .getData()
                .get("subscriberKey");
        final int subscriber2 = (int) apiRule
                .openTaskSubscription(taskType)
                .await()
                .getData()
                .get("subscriberKey");

        // when
        testClient.createTask(taskType);
        testClient.createTask(taskType);
        testClient.createTask(taskType);
        testClient.createTask(taskType);

        // then
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 4);

        final List<SubscribedRecord> receivedEvents = apiRule.subscribedEvents().limit(4)
                .collect(Collectors.toList());

        final long firstReceivingSubscriber = receivedEvents.get(0).subscriberKey();
        final long secondReceivingSubscriber = firstReceivingSubscriber == subscriber1 ? subscriber2 : subscriber1;

        assertThat(receivedEvents).extracting(e -> e.subscriberKey()).containsExactly(
                firstReceivingSubscriber,
                secondReceivingSubscriber,
                firstReceivingSubscriber,
                secondReceivingSubscriber);
    }

    @Test
    public void shouldCloseSubscriptionOnTransportChannelClose() throws InterruptedException
    {
        // given
        apiRule
            .openTaskSubscription("foo")
            .await();

        // when the transport channel is closed
        apiRule.interruptAllChannels();

        // then the subscription has been closed, so we can create a new task and lock it for a new subscription
        Thread.sleep(1000L); // closing subscriptions happens asynchronously

        final ExecuteCommandResponse response = testClient.createTask("foo");

        final ControlMessageResponse subscriptionResponse = apiRule
            .openTaskSubscription("foo")
            .await();
        final int secondSubscriberKey = (int) subscriptionResponse.getData().get("subscriberKey");

        final Optional<SubscribedRecord> taskEvent = apiRule.subscribedEvents()
            .filter((s) -> s.subscriptionType() == SubscriptionType.TASK_SUBSCRIPTION
                && s.key() == response.key())
            .findFirst();

        assertThat(taskEvent).isPresent();
        assertThat(taskEvent.get().subscriberKey()).isEqualTo(secondSubscriberKey);
    }

    @Test
    public void shouldContinueRoundRobinTaskDistributionAfterClientChannelClose()
    {
        // given
        apiRule
            .openTaskSubscription("foo")
            .await();

        apiRule
            .openTaskSubscription("foo")
            .await();

        testClient.createTask("foo");
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 1);

        openAndCloseConnectionTo(apiRule.getBrokerAddress());

        // when
        testClient.createTask("foo");
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 2);

        // then
        final List<SubscribedRecord> events = apiRule.subscribedEvents().limit(2).collect(Collectors.toList());
        assertThat(events.get(0).subscriberKey()).isNotEqualTo(events.get(1).subscriberKey());

    }

    protected void openAndCloseConnectionTo(SocketAddress remote)
    {
        try
        {
            final SocketChannel media = SocketChannel.open();
            media.setOption(StandardSocketOptions.TCP_NODELAY, true);
            media.configureBlocking(true);
            media.connect(remote.toInetSocketAddress());
            media.close();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void shouldRejectCreditsEqualToZero()
    {
        // when
        final ErrorResponse error = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.INCREASE_TASK_SUBSCRIPTION_CREDITS)
            .data()
                .put("subscriberKey", 1)
                .put("credits", 0)
                .put("partitionId", apiRule.getDefaultPartitionId())
                .done()
            .send().awaitError();

        // then
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(error.getErrorData()).isEqualTo("Cannot increase task subscription credits. Credits must be positive.");
    }


    @Test
    public void shouldRejectNegativeCredits()
    {
        // when
        final ErrorResponse error = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.INCREASE_TASK_SUBSCRIPTION_CREDITS)
            .data()
                .put("subscriberKey", 1)
                .put("credits", -10)
                .put("partitionId", apiRule.getDefaultPartitionId())
                .done()
            .send().awaitError();

        // then
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(error.getErrorData()).isEqualTo("Cannot increase task subscription credits. Credits must be positive.");
    }

    @Test
    public void shouldAddTaskSubscriptionsForDifferentTypes()
    {
        // given
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TASK_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("taskType", "foo")
                .put("lockDuration", 1000L)
                .put("lockOwner", "owner1")
                .put("credits", 5)
                .done()
            .send();

        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TASK_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("taskType", "bar")
                .put("lockDuration", 1000L)
                .put("lockOwner", "owner2")
                .put("credits", 5)
                .done()
            .send();

        // when
        testClient.createTask("foo");
        testClient.createTask("bar");

        // then
        final List<SubscribedRecord> taskEvents = testClient.receiveEvents()
                .ofTypeTask()
                .withIntent(TaskIntent.LOCKED)
                .limit(2)
                .collect(Collectors.toList());

        assertThat(taskEvents).hasSize(2);

        assertThat(taskEvents.get(0).value())
            .containsEntry("type", "foo")
            .containsEntry("lockOwner", "owner1");

        assertThat(taskEvents.get(1).value())
            .containsEntry("type", "bar")
            .containsEntry("lockOwner", "owner2");
    }

    @Test
    public void shouldLockTasksUntilCreditsAreExhausted() throws InterruptedException
    {
        // given
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TASK_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("taskType", "foo")
                .put("lockDuration", 1000L)
                .put("lockOwner", "bar")
                .put("credits", 2)
                .done()
            .send();

        // when
        testClient.createTask("foo");
        testClient.createTask("foo");
        testClient.createTask("foo");

        // then
        Thread.sleep(500);
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 2);

        final List<SubscribedRecord> taskEvents = testClient.receiveEvents()
                .ofTypeTask()
                .withIntent(TaskIntent.LOCKED)
                .limit(2)
                .collect(Collectors.toList());

        assertThat(taskEvents)
            .extracting(s -> s.value().get("lockOwner"))
            .contains("bar");
    }

    @Test
    public void shouldIncreaseSubscriptionCredits() throws InterruptedException
    {
        // given
        final ControlMessageResponse response = apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TASK_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("taskType", "foo")
                .put("lockDuration", 1000L)
                .put("lockOwner", "bar")
                .put("credits", 2)
                .done()
            .sendAndAwait();

        assertThat(response.getData().get("subscriberKey")).isEqualTo(0);

        testClient.createTask("foo");
        testClient.createTask("foo");

        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 2);

        testClient.createTask("foo");
        testClient.createTask("foo");

        // when
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.INCREASE_TASK_SUBSCRIPTION_CREDITS)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("subscriberKey", 0)
                .put("credits", 2)
                .put("partitionId", apiRule.getDefaultPartitionId())
                .done()
            .send();

        // then
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 4);
    }

    @Test
    public void shouldIgnoreCreditsRequestIfSubscriptionDoesNotExist()
    {
        // given
        final int nonExistingSubscriberKey = 444;
        final ControlMessageRequestBuilder request = apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.INCREASE_TASK_SUBSCRIPTION_CREDITS)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("subscriberKey", nonExistingSubscriberKey)
                .put("credits", 2)
                .put("partitionId", apiRule.getDefaultPartitionId())
                .done();

        // when
        final ControlMessageResponse response = request.sendAndAwait();

        // then
        assertThat(response.getData()).containsEntry("subscriberKey", nonExistingSubscriberKey);
    }

    @Test
    public void shouldNotPublishTaskWithoutRetries() throws InterruptedException
    {
        // given
        final String taskType = "foo";
        apiRule.openTaskSubscription(taskType).await();

        testClient.createTask(taskType);

        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 1);
        final SubscribedRecord task = apiRule.subscribedEvents().findFirst().get();

        // when
        final Map<String, Object> event = new HashMap<>(task.value());
        event.put("retries", 0);
        testClient.failTask(task.key(), event);

        // then
        Thread.sleep(500);

        assertThat(apiRule.numSubscribedEventsAvailable()).isEqualTo(0);
    }

    @Test
    public void shouldNotPublishTaskOfDifferentType() throws InterruptedException
    {
        // given
        final String taskType = "foo";
        apiRule.openTaskSubscription(taskType).await();

        // when
        testClient.createTask("bar");

        // then
        Thread.sleep(500);
        assertThat(apiRule.numSubscribedEventsAvailable()).isEqualTo(0);
    }

    @Test
    public void shouldPublishTasksToSecondSubscription()
    {
        // given
        final int credits = 2;
        final String taskType = "foo";
        apiRule.openTaskSubscription(apiRule.getDefaultPartitionId(), taskType, 10000, credits).await();

        for (int i = 0; i < credits; i++)
        {
            testClient.createTask(taskType);
        }

        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == credits);

        apiRule.moveMessageStreamToTail();

        final int secondSubscriber = (int) apiRule
                .openTaskSubscription(apiRule.getDefaultPartitionId(), taskType, 10000, credits)
                .await()
                .getData()
                .get("subscriberKey");

        // when
        testClient.createTask(taskType);

        // then
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 1);

        final SubscribedRecord subscribedEvent = apiRule.subscribedEvents().findFirst().get();
        assertThat(subscribedEvent.subscriberKey()).isEqualTo(secondSubscriber);
    }

}
