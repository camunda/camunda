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

import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.taskEvents;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.protocol.clientapi.*;
import io.zeebe.test.broker.protocol.clientapi.*;
import io.zeebe.transport.SocketAddress;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;


public class TaskSubscriptionTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

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
                .put("lockDuration", 1000L)
                .put("lockOwner", "bar")
                .put("credits", 5)
                .done()
            .send();

        // when
        final ExecuteCommandResponse response = createTask("foo");

        // then
        final SubscribedEvent taskEvent = apiRule.topic().receiveSingleEvent(taskEvents("LOCKED"));
        assertThat(taskEvent.key()).isEqualTo(response.key());
        assertThat(taskEvent.position()).isGreaterThan(response.position());
        assertThat(taskEvent.event())
            .containsEntry("type", "foo")
            .containsEntry("retries", 3)
            .containsEntry("lockOwner", "bar");
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

        final ExecuteCommandResponse response = createTask("foo");

        final ControlMessageResponse subscriptionResponse = apiRule
            .openTaskSubscription("foo")
            .await();
        final int secondSubscriberKey = (int) subscriptionResponse.getData().get("subscriberKey");

        final Optional<SubscribedEvent> taskEvent = apiRule.subscribedEvents()
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

        createTask("foo");
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 1);

        openAndCloseConnectionTo(apiRule.getBrokerAddress());

        // when
        createTask("foo");
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 2);

        // then
        final List<SubscribedEvent> events = apiRule.subscribedEvents().limit(2).collect(Collectors.toList());
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
        createTask("foo");
        createTask("bar");

        // then
        final List<SubscribedEvent> taskEvents = apiRule.topic().receiveEvents(taskEvents("LOCKED"))
                .limit(2)
                .collect(Collectors.toList());

        assertThat(taskEvents).hasSize(2);

        assertThat(taskEvents.get(0).event())
            .containsEntry("type", "foo")
            .containsEntry("lockOwner", "owner1");

        assertThat(taskEvents.get(1).event())
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
        createTask("foo");
        createTask("foo");

        // then
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 2);

        final List<SubscribedEvent> taskEvents = apiRule.topic().receiveEvents(taskEvents("LOCKED"))
                .limit(2)
                .collect(Collectors.toList());

        assertThat(taskEvents)
            .extracting(s -> s.event().get("lockOwner"))
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

        createTask("foo");
        createTask("foo");

        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 2);

        createTask("foo");
        createTask("foo");

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


    private ExecuteCommandResponse createTask(String type)
    {
        return apiRule.createCmdRequest()
                .eventTypeTask()
                .command()
                    .put("state", "CREATE")
                    .put("type", type)
                    .put("retries", 3)
                .done()
                .sendAndAwait();
    }

}
