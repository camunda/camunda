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
package io.zeebe.client.event;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.zeebe.client.task.impl.subscription.SubscriptionManager;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.impl.TopicSubscriberGroup;
import io.zeebe.client.event.impl.TopicSubscriptionBuilderImpl;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.brokerapi.ControlMessageRequest;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.ResponseController;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.test.util.Conditions;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.sched.future.ActorFuture;

public class TopicSubscriptionTest
{

    protected static final String SUBSCRIPTION_NAME = "foo";

    protected static final UniversalEventHandler DO_NOTHING = e ->
    { };

    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule broker = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(broker).around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected ZeebeClient client;

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();
    }

    @Test
    public void shouldOpenSubscription()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        // when
        clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
            .startAtHeadOfTopic()
            .handler(DO_NOTHING)
            .name(SUBSCRIPTION_NAME)
            .open();

        // then
        final ExecuteCommandRequest subscribeRequest = broker.getReceivedCommandRequests()
            .stream()
            .filter((e) -> e.eventType() == EventType.SUBSCRIBER_EVENT)
            .findFirst()
            .get();

        assertThat(subscribeRequest.getCommand())
            .containsEntry("state", "SUBSCRIBE")
            .containsEntry("startPosition", 0)
            .containsEntry("prefetchCapacity", 32)
            .containsEntry("name", SUBSCRIPTION_NAME)
            .doesNotContainEntry("forceStart", true);
    }

    @Test
    public void shouldOpenSubscriptionAndForceStart()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        // when
        clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
            .startAtHeadOfTopic()
            .forcedStart()
            .handler(DO_NOTHING)
            .name(SUBSCRIPTION_NAME)
            .open();

        // then
        final ExecuteCommandRequest subscribeRequest = broker.getReceivedCommandRequests()
            .stream()
            .filter((e) -> e.eventType() == EventType.SUBSCRIBER_EVENT)
            .findFirst()
            .get();

        assertThat(subscribeRequest.getCommand()).containsEntry("forceStart", true);
    }

    @Test
    public void shouldOpenSubscriptionAtTailOfTopic()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        // when
        clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
            .startAtTailOfTopic()
            .handler(DO_NOTHING)
            .name(SUBSCRIPTION_NAME)
            .open();

        // then
        final ExecuteCommandRequest subscribeRequest = broker.getReceivedCommandRequests()
            .stream()
            .filter((e) -> e.eventType() == EventType.SUBSCRIBER_EVENT)
            .findFirst()
            .get();

        assertThat(subscribeRequest.getCommand())
            .hasEntrySatisfying("startPosition", Conditions.isLowerThan(0))
            .containsEntry("state", "SUBSCRIBE")
            .containsEntry("prefetchCapacity", 32)
            .containsEntry("name", SUBSCRIPTION_NAME)
            .doesNotContainEntry("forceStart", true);
    }

    @Test
    public void shouldOpenSubscriptionAtPosition()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        // when
        clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
            .startAtPosition(clientRule.getDefaultPartitionId(), 654L)
            .handler(DO_NOTHING)
            .name(SUBSCRIPTION_NAME)
            .open();

        // then
        final ExecuteCommandRequest subscribeRequest = broker.getReceivedCommandRequests()
            .stream()
            .filter((e) -> e.eventType() == EventType.SUBSCRIBER_EVENT)
            .findFirst()
            .get();

        assertThat(subscribeRequest.getCommand())
            .containsEntry("startPosition", 654)
            .containsEntry("state", "SUBSCRIBE")
            .containsEntry("prefetchCapacity", 32)
            .containsEntry("name", SUBSCRIPTION_NAME)
            .doesNotContainEntry("forceStart", true);
    }

    @Test
    public void shouldOpenPollableSubscription()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        // when
        clientRule.topics().newPollableSubscription(clientRule.getDefaultTopicName())
            .startAtHeadOfTopic()
            .name(SUBSCRIPTION_NAME)
            .open();

        // then
        final ExecuteCommandRequest subscribeRequest = broker.getReceivedCommandRequests()
            .stream()
            .filter((e) -> e.eventType() == EventType.SUBSCRIBER_EVENT)
            .findFirst()
            .get();

        assertThat(subscribeRequest.getCommand())
            .containsEntry("state", "SUBSCRIBE")
            .containsEntry("startPosition", 0)
            .containsEntry("prefetchCapacity", 32)
            .containsEntry("name", SUBSCRIPTION_NAME)
            .doesNotContainEntry("forceStart", true);
    }

    @Test
    public void shouldValidateEventHandlerForManagedSubscription()
    {
        // given
        final TopicSubscriptionBuilder builder = clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
            .startAtHeadOfTopic()
            .name(SUBSCRIPTION_NAME);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("at least one handler must be set");

        // when
        builder.open();
    }

    @Test
    public void shouldValidateNameForManagedSubscription()
    {
        // given
        final TopicSubscriptionBuilder builder = clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
                .startAtHeadOfTopic()
                .handler(DO_NOTHING);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("name must not be null");

        // when
        builder.open();
    }


    @Test
    public void shouldValidateNameForPollableSubscription()
    {
        // given
        final PollableTopicSubscriptionBuilder builder = clientRule.topics().newPollableSubscription(clientRule.getDefaultTopicName())
                .startAtHeadOfTopic();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("name must not be null");

        // when
        builder.open();
    }

    @Test
    public void shouldRetryThreeTimesOnHandlerFailure() throws InterruptedException
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final FailingHandler handler = new FailingHandler();
        final TopicSubscription subscription = clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
            .startAtHeadOfTopic()
            .handler(handler)
            .name(SUBSCRIPTION_NAME)
            .open();

        final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

        // when pushing two events
        broker.pushTopicEvent(clientAddress, 123L, 1L, 1L);
        broker.pushTopicEvent(clientAddress, 123L, 1L, 2L);

        // then
        waitUntil(() -> !subscription.isOpen());
        Thread.sleep(1000L); // wait an extra second as we might receive more events if this feature is broken

        assertThat(handler.getRecordedEvents()).hasSize(3);

        final Set<Long> eventPositions = handler.getRecordedEvents().stream()
            .map((re) -> re.getMetadata().getPosition())
            .collect(Collectors.toSet());

        assertThat(eventPositions).containsExactly(1L);
    }

    @Test
    public void shouldResumeSubscriptionBeforeFailedEventAfterHandlerFailure()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final FailingHandler handler = new FailingHandler(e -> e.getMetadata().getPosition() == 2L);
        final TopicSubscription subscription = clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
                .startAtHeadOfTopic()
                .handler(handler)
                .name(SUBSCRIPTION_NAME)
                .open();

        final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();
        broker.pushTopicEvent(clientAddress, 123L, 1L, 1L);

        // when
        broker.pushTopicEvent(clientAddress, 123L, 1L, 2L);

        // then
        TestUtil.waitUntil(() -> subscription.isClosed());

        final List<ExecuteCommandRequest> commandRequests = broker.getReceivedCommandRequests();

        final List<ExecuteCommandRequest> acknowledgements = commandRequests.stream()
                .filter((c) -> c.eventType() == EventType.SUBSCRIPTION_EVENT)
                .filter((c) -> "ACKNOWLEDGE".equals(c.getCommand().get("state")))
                .collect(Collectors.toList());

        assertThat(acknowledgements).isNotEmpty();

        final ExecuteCommandRequest lastAck = acknowledgements.get(acknowledgements.size() - 1);
        assertThat(lastAck.getCommand().get("name")).isEqualTo(SUBSCRIPTION_NAME);
        assertThat(lastAck.getCommand().get("ackPosition")).isEqualTo(1);

        final ControlMessageRequest removeRequest = broker.getReceivedControlMessageRequests().stream()
                .filter((c) -> c.messageType() == ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
                .findFirst()
                .get();

        final List<Object> requests = broker.getAllReceivedRequests();
        assertThat(requests).contains(lastAck);
        assertThat(requests.indexOf(lastAck)).isLessThan(requests.indexOf(removeRequest));
    }

    @Test
    public void shouldContinueEventHandlingAfterSuccessfulRetry()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final AtomicInteger counter = new AtomicInteger(3);
        final FailingHandler handler = new FailingHandler(e ->
                e.getMetadata().getPosition() == 1L &&
                counter.decrementAndGet() > 0);

        clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
            .startAtHeadOfTopic()
            .handler(handler)
            .name(SUBSCRIPTION_NAME)
            .open();

        final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();
        broker.pushTopicEvent(clientAddress, 123L, 1L, 1L);

        // when
        broker.pushTopicEvent(clientAddress, 123L, 1L, 2L);

        // then
        TestUtil.waitUntil(() -> handler
                .getRecordedEvents()
                .stream()
                .anyMatch(re -> re.getMetadata().getPosition() == 2L));

        final List<Long> handledEventPositions = handler
            .getRecordedEvents()
            .stream()
            .map((re) -> re.getMetadata().getPosition())
            .collect(Collectors.toList());

        assertThat(handledEventPositions).containsExactly(1L, 1L, 1L, 2L);
    }

    @Test
    public void shouldSendPrefetchCapacityAsDefinedInClientProperties()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        // when
        clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
            .startAtHeadOfTopic()
            .handler(DO_NOTHING)
            .name(SUBSCRIPTION_NAME)
            .open();

        // then
        final ExecuteCommandRequest addSubscriptionRequest = broker.getReceivedCommandRequests().stream()
            .filter((r) -> r.eventType() == EventType.SUBSCRIBER_EVENT && "SUBSCRIBE".equals(r.getCommand().get("state")))
            .findFirst()
            .get();

        assertThat(addSubscriptionRequest.getCommand()).containsEntry("prefetchCapacity", 32);
    }

    @Test
    public void shouldOnlyAcknowledgeEventAndCloseSubscriptionAfterLastEventHasBeenHandled() throws InterruptedException, ExecutionException, TimeoutException
    {
        // given
        broker.stubTopicSubscriptionApi(123L);
        final ControllableHandler handler = new ControllableHandler();

        final TopicSubscriberGroup subscription = (TopicSubscriberGroup) clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
            .startAtHeadOfTopic()
            .handler(handler)
            .name(SUBSCRIPTION_NAME)
            .open();

        final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

        broker.pushTopicEvent(clientAddress, 123L, 1L, 1L);
        TestUtil.waitUntil(() -> handler.isWaiting());

        // when
        final ActorFuture<?> closeFuture = subscription.closeAsync();

        // then
        Thread.sleep(1000L);
        assertThat(closeFuture).isNotDone();

        boolean hasSentAck = broker.getReceivedCommandRequests().stream()
            .filter((r) -> r.eventType() == EventType.SUBSCRIPTION_EVENT)
            .findAny()
            .isPresent();

        assertThat(hasSentAck).isFalse();

        // and when
        handler.signal();

        // then
        closeFuture.get(1L, TimeUnit.SECONDS);

        // and
        hasSentAck = broker.getReceivedCommandRequests().stream()
            .filter((r) -> r.eventType() == EventType.SUBSCRIPTION_EVENT)
            .findAny()
            .isPresent();

        assertThat(hasSentAck).isTrue();
    }

    @Test
    public void shouldCloseSubscriptionOnChannelClose() throws InterruptedException
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final TopicSubscription subscription = clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
            .startAtHeadOfTopic()
            .handler(DO_NOTHING)
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        broker.closeTransport();
        Thread.sleep(500L); // let subscriber attempt reopening
        clientRule.getClock().addTime(Duration.ofSeconds(60)); // make request time out immediately

        // then
        TestUtil.waitUntil(() -> subscription.isClosed());
        assertThat(subscription.isClosed()).isTrue();
    }

    @Test
    public void shouldCloseSubscriptionOnClientClose()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final TopicSubscription subscription = clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
            .startAtHeadOfTopic()
            .handler(DO_NOTHING)
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        client.close();

        // then
        assertThat(subscription.isClosed()).isTrue();
    }

    @Test
    public void shouldAllowReopeningSubscriptionAfterChannelClose() throws InterruptedException
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final TopicSubscription firstSubscription = clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
            .startAtHeadOfTopic()
            .handler(DO_NOTHING)
            .name(SUBSCRIPTION_NAME)
            .open();
        firstSubscription.close();

        broker.closeTransport();

        broker.bindTransport();

        // when
        final TopicSubscription secondSubscription = clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
                .startAtHeadOfTopic()
                .handler(DO_NOTHING)
                .name(SUBSCRIPTION_NAME)
                .open();

        // then
        assertThat(secondSubscription.isOpen()).isTrue();
    }

    @Test
    public void shouldInvokeDefaultHandlerForTopicEvent()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final RecordingTopicEventHandler eventHandler = new RecordingTopicEventHandler();

        clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
            .startAtHeadOfTopic()
            .handler(eventHandler)
            .name(SUBSCRIPTION_NAME)
            .open();

        final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

        // when pushing two events
        broker.pushTopicEvent(clientAddress, 123L, 1L, 1L, EventType.RAFT_EVENT);
        broker.pushTopicEvent(clientAddress, 123L, 1L, 2L, EventType.RAFT_EVENT);

        // then
        waitUntil(() -> eventHandler.numTopicEvents() == 2);

        final GeneralEvent event1 = eventHandler.topicEvents.get(0);
        final GeneralEvent event2 = eventHandler.topicEvents.get(1);

        assertMetadata(event1, 1L, 1L, TopicEventType.RAFT);
        assertMetadata(event2, 1L, 2L, TopicEventType.RAFT);

        assertThat(eventHandler.numTopicEvents()).isEqualTo(2);
        assertThat(eventHandler.numTaskEvents()).isEqualTo(0);
        assertThat(eventHandler.numWorkflowEvents()).isEqualTo(0);
        assertThat(eventHandler.numWorkflowInstanceEvents()).isEqualTo(0);
        assertThat(eventHandler.numIncidentEvents()).isEqualTo(0);
    }

    @Test
    public void shouldInvokeTasktHandlerForTaskEvent()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final RecordingTopicEventHandler eventHandler = subscribeToAllEvents();

        final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

        // when pushing two events
        broker.pushTopicEvent(clientAddress, 123L, 1L, 1L, EventType.TASK_EVENT);
        broker.pushTopicEvent(clientAddress, 123L, 1L, 2L, EventType.TASK_EVENT);

        // then
        waitUntil(() -> eventHandler.numTaskEvents() >= 2);

        final TaskEvent event1 = eventHandler.taskEvents.get(0);
        final TaskEvent event2 = eventHandler.taskEvents.get(1);

        assertMetadata(event1, 1L, 1L, TopicEventType.TASK);
        assertMetadata(event2, 1L, 2L, TopicEventType.TASK);

        assertThat(eventHandler.numTopicEvents()).isEqualTo(0);
        assertThat(eventHandler.numTaskEvents()).isEqualTo(2);
        assertThat(eventHandler.numWorkflowEvents()).isEqualTo(0);
        assertThat(eventHandler.numWorkflowInstanceEvents()).isEqualTo(0);
        assertThat(eventHandler.numIncidentEvents()).isEqualTo(0);
    }

    @Test
    public void shouldInvokeWorkflowHandlerForWorkflowEvent()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final RecordingTopicEventHandler eventHandler = subscribeToAllEvents();

        final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

        // when pushing two events
        broker.pushTopicEvent(clientAddress, 123L, 1L, 1L, EventType.WORKFLOW_EVENT);
        broker.pushTopicEvent(clientAddress, 123L, 1L, 2L, EventType.WORKFLOW_EVENT);

        // then
        waitUntil(() -> eventHandler.numWorkflowEvents() >= 2);

        final WorkflowEvent event1 = eventHandler.workflowEvents.get(0);
        final WorkflowEvent event2 = eventHandler.workflowEvents.get(1);

        assertMetadata(event1, 1L, 1L, TopicEventType.WORKFLOW);
        assertMetadata(event2, 1L, 2L, TopicEventType.WORKFLOW);

        assertThat(eventHandler.numTopicEvents()).isEqualTo(0);
        assertThat(eventHandler.numTaskEvents()).isEqualTo(0);
        assertThat(eventHandler.numWorkflowEvents()).isEqualTo(2);
        assertThat(eventHandler.numWorkflowInstanceEvents()).isEqualTo(0);
        assertThat(eventHandler.numIncidentEvents()).isEqualTo(0);
    }

    @Test
    public void shouldInvokeWorkflowInstanceHandlerForWorkflowInstanceEvent()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final RecordingTopicEventHandler eventHandler = subscribeToAllEvents();

        final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

        // when pushing two events
        broker.pushTopicEvent(clientAddress, 123L, 1L, 1L, EventType.WORKFLOW_INSTANCE_EVENT);
        broker.pushTopicEvent(clientAddress, 123L, 1L, 2L, EventType.WORKFLOW_INSTANCE_EVENT);

        // then
        waitUntil(() -> eventHandler.numWorkflowInstanceEvents() >= 2);

        final WorkflowInstanceEvent event1 = eventHandler.workflowInstanceEvents.get(0);
        final WorkflowInstanceEvent event2 = eventHandler.workflowInstanceEvents.get(1);

        assertMetadata(event1, 1L, 1L, TopicEventType.WORKFLOW_INSTANCE);
        assertMetadata(event2, 1L, 2L, TopicEventType.WORKFLOW_INSTANCE);

        assertThat(eventHandler.numTopicEvents()).isEqualTo(0);
        assertThat(eventHandler.numTaskEvents()).isEqualTo(0);
        assertThat(eventHandler.numWorkflowEvents()).isEqualTo(0);
        assertThat(eventHandler.numWorkflowInstanceEvents()).isEqualTo(2);
        assertThat(eventHandler.numIncidentEvents()).isEqualTo(0);
    }

    @Test
    public void shouldInvokeIncidentEventHandlerForIncidentEvent()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final RecordingTopicEventHandler eventHandler = subscribeToAllEvents();

        final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

        // when pushing two events
        broker.pushTopicEvent(clientAddress, 123L, 1L, 1L, EventType.INCIDENT_EVENT);
        broker.pushTopicEvent(clientAddress, 123L, 1L, 2L, EventType.INCIDENT_EVENT);

        // then
        waitUntil(() -> eventHandler.numIncidentEvents() >= 2);

        final IncidentEvent event1 = eventHandler.incidentEvents.get(0);
        final IncidentEvent event2 = eventHandler.incidentEvents.get(1);

        assertMetadata(event1, 1L, 1L, TopicEventType.INCIDENT);
        assertMetadata(event2, 1L, 2L, TopicEventType.INCIDENT);

        assertThat(eventHandler.numTopicEvents()).isEqualTo(0);
        assertThat(eventHandler.numTaskEvents()).isEqualTo(0);
        assertThat(eventHandler.numWorkflowEvents()).isEqualTo(0);
        assertThat(eventHandler.numWorkflowInstanceEvents()).isEqualTo(0);
        assertThat(eventHandler.numIncidentEvents()).isEqualTo(2);
    }

    @Test
    public void shouldInvokeRaftEventHandlerForRaftEvent()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final RecordingTopicEventHandler eventHandler = subscribeToAllEvents();

        final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

        // when pushing two events
        broker.pushTopicEvent(clientAddress, 123L, 1L, 1L, EventType.RAFT_EVENT);
        broker.pushTopicEvent(clientAddress, 123L, 1L, 2L, EventType.RAFT_EVENT);

        // then
        waitUntil(() -> eventHandler.numRaftEvents() >= 2);

        final RaftEvent event1 = eventHandler.raftEvents.get(0);
        final RaftEvent event2 = eventHandler.raftEvents.get(1);

        assertMetadata(event1, 1L, 1L, TopicEventType.RAFT);
        assertMetadata(event2, 1L, 2L, TopicEventType.RAFT);

        assertThat(eventHandler.numTopicEvents()).isEqualTo(0);
        assertThat(eventHandler.numTaskEvents()).isEqualTo(0);
        assertThat(eventHandler.numWorkflowEvents()).isEqualTo(0);
        assertThat(eventHandler.numWorkflowInstanceEvents()).isEqualTo(0);
        assertThat(eventHandler.numIncidentEvents()).isEqualTo(0);
        assertThat(eventHandler.numRaftEvents()).isEqualTo(2);
    }

    protected RecordingTopicEventHandler subscribeToAllEvents()
    {

        final RecordingTopicEventHandler eventHandler = new RecordingTopicEventHandler();

        clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
            .startAtHeadOfTopic()
            .handler(eventHandler)
            .taskEventHandler(eventHandler)
            .workflowEventHandler(eventHandler)
            .workflowInstanceEventHandler(eventHandler)
            .incidentEventHandler(eventHandler)
            .raftEventHandler(eventHandler)
            .name(SUBSCRIPTION_NAME)
            .open();

        return eventHandler;
    }

    @Test
    public void shouldInvokeDefaultHandlerIfNoHandlerIsRegistered()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final RecordingTopicEventHandler defaultEventHandler = new RecordingTopicEventHandler();

        clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
            .startAtHeadOfTopic()
            .handler(defaultEventHandler)
            .name(SUBSCRIPTION_NAME)
            .open();

        final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

        // when pushing two events
        broker.pushTopicEvent(clientAddress, 123L, 1L, 1L, EventType.TASK_EVENT);
        broker.pushTopicEvent(clientAddress, 123L, 1L, 2L, EventType.WORKFLOW_INSTANCE_EVENT);
        broker.pushTopicEvent(clientAddress, 123L, 1L, 3L, EventType.INCIDENT_EVENT);

        // then
        waitUntil(() -> defaultEventHandler.numTopicEvents() == 3);

        assertThat(defaultEventHandler.numTopicEvents()).isEqualTo(3);
    }

    @Test
    public void shouldReopenSubscriptionOnChannelInterruption() throws InterruptedException
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
            .startAtHeadOfTopic()
            .handler(DO_NOTHING)
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        broker.interruptAllServerChannels();

        // then
        final ExecuteCommandRequest reopenRequest = TestUtil.doRepeatedly(() -> receivedSubscribeCommands()
                .skip(1)
                .findFirst())
            .until(v -> v.isPresent())
            .get();

        assertThat(reopenRequest.getCommand())
            .containsEntry("state", "SUBSCRIBE")
            .containsEntry("startPosition", 0)
            .containsEntry("prefetchCapacity", 32)
            .containsEntry("name", SUBSCRIPTION_NAME)
            .doesNotContainEntry("forceStart", true);
    }

    @Test
    public void shouldReceiveEventsAfterChannelInterruption()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);
        final RecordingEventHandler recordingHandler = new RecordingEventHandler();

        clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
            .startAtHeadOfTopic()
            .handler(recordingHandler)
            .name(SUBSCRIPTION_NAME)
            .open();

        broker.interruptAllServerChannels();

        TestUtil.waitUntil(() -> receivedSubscribeCommands().count() >= 2);

        final RemoteAddress clientAddress = receivedSubscribeCommands().skip(1).findFirst().get().getSource();

        // when
        broker.pushTopicEvent(clientAddress, 124L, 1L, 2L);

        // then
        TestUtil.waitUntil(() -> recordingHandler.getRecordedEvents().size() > 0);
        assertThat(recordingHandler.getRecordedEvents()).hasSize(1);
    }

    @Test
    public void shouldDiscardEventsReceivedBeforeReopening() throws InterruptedException
    {
        // given
        broker.stubTopicSubscriptionApi(123L);
        final ControllableHandler handler = new ControllableHandler();

        clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
            .startAtHeadOfTopic()
            .handler(handler)
            .name(SUBSCRIPTION_NAME)
            .open();

        final RemoteAddress clientAddress = receivedSubscribeCommands().findFirst().get().getSource();

        broker.pushTopicEvent(clientAddress, 123L, 1L, 2L);
        broker.pushTopicEvent(clientAddress, 123L, 1L, 3L);

        TestUtil.waitUntil(() -> handler.isWaiting());

        // when
        broker.interruptAllServerChannels();

        TestUtil.waitUntil(() -> receivedSubscribeCommands().count() >= 2);

        handler.disableWait();
        handler.signal();

        // then
        // give client some time to invoke handler with second event
        Thread.sleep(1000L);

        assertThat(handler.getNumHandledEvents()).isEqualTo(1);
    }

    @Test
    public void testValidateTopicNameNotNull()
    {
        // given
        final ZeebeClient client = clientRule.getClient();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("topic must not be null");

        // when
        client.topics().newSubscription(null);
    }

    @Test
    public void testValidateTopicNameNotEmpty()
    {
        // given
        final ZeebeClient client = clientRule.getClient();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("topic must not be empty");

        // when
        client.topics().newSubscription("");
    }

    @Test
    public void shouldCloseSubscriptionWhileOpeningSubscriber()
    {
        // given
        final int subscriberKey = 123;

        broker.stubTopicSubscriptionApi(0L);
        final ResponseController responseController = broker.onExecuteCommandRequest(EventType.SUBSCRIBER_EVENT, "SUBSCRIBE")
            .respondWith()
            .key(subscriberKey)
            .value()
                .allOf((r) -> r.getCommand())
                .put("state", "SUBSCRIBED")
                .done()
            .registerControlled();

        final TopicSubscriptionBuilderImpl builder = (TopicSubscriptionBuilderImpl) client.topics().newSubscription(clientRule.getDefaultTopicName())
            .handler(DO_NOTHING)
            .name("foo");

        final Future<TopicSubscriberGroup> future = builder.buildSubscriberGroup();

        waitUntil(() ->
            broker.getReceivedCommandRequests().stream()
                .filter(r -> r.eventType() == EventType.SUBSCRIBER_EVENT && "SUBSCRIBE".equals(r.getCommand().get("state")))
                .count() == 1);

        final Thread closingThread = new Thread(client::close);
        closingThread.start();


        final SubscriptionManager subscriptionManager = ((ZeebeClientImpl) client).getSubscriptionManager();
        waitUntil(() -> subscriptionManager.isClosing());
        // when
        responseController.unblockNextResponse();

        // then
        waitUntil(() -> future.isDone());

        assertThat(future).isDone();

        final Optional<ControlMessageRequest> closeRequest = broker.getReceivedControlMessageRequests().stream()
            .filter(c -> c.messageType() == ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .findFirst();

        assertThat(closeRequest).isPresent();
        final ControlMessageRequest request = closeRequest.get();
        assertThat(request.getData()).containsEntry("subscriberKey", subscriberKey);
    }

    @Test
    public void shouldCloseClientAfterSubscriptionCloseIsCalled() throws Exception
    {
        // given
        broker.stubTopicSubscriptionApi(0L);

        final ResponseController responseController = broker.onControlMessageRequest((r) -> r.messageType() == ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .respondWith()
            .data()
            .allOf((r) -> r.getData())
            .done()
            .registerControlled();

        final TopicSubscription foo = client.topics().newSubscription(clientRule.getDefaultTopicName())
            .handler(DO_NOTHING)
            .name("foo")
            .open();

        final ActorFuture<Void> future = ((TopicSubscriberGroup) foo).closeAsync();

        waitUntil(() ->
            broker.getReceivedControlMessageRequests().stream()
                .filter(r -> r.messageType() == ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
                .count() == 1);

        final Thread closingThread = new Thread(client::close);
        closingThread.start();

        // when
        responseController.unblockNextResponse();

        // then
        closingThread.join();
        waitUntil(() -> future.isDone());

        assertThat(future).isDone();
    }

    @Test
    public void shouldCloseSubscriptionWhenAckFails()
    {
        // given
        final long subscriberKey = 123L;
        broker.stubTopicSubscriptionApi(subscriberKey);
        broker.onExecuteCommandRequest(EventType.SUBSCRIPTION_EVENT, "ACKNOWLEDGE")
            .respondWithError()
            .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
            .errorData("foo")
            .register();

        final TopicSubscription subscription = clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
            .startAtHeadOfTopic()
            .handler(DO_NOTHING)
            .name(SUBSCRIPTION_NAME)
            .open();

        final RemoteAddress clientAddress = receivedSubscribeCommands().findFirst().get().getSource();
        final int subscriptionCapacity = ((ZeebeClientImpl) client).getSubscriptionPrefetchCapacity();

        // when
        for (int i = 0; i < subscriptionCapacity; i++)
        {
            broker.pushTopicEvent(clientAddress, subscriberKey, i, i);
        }

        // then
        waitUntil(() -> subscription.isClosed());

        assertThat(subscription.isClosed()).isTrue();
    }

    @Test
    public void shouldCloseSubscriptionWhenClosingFails()
    {
        // given
        final long subscriberKey = 123L;
        broker.stubTopicSubscriptionApi(subscriberKey);
        broker.onControlMessageRequest(r -> r.messageType() == ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .respondWithError()
            .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
            .errorData("foo")
            .register();

        final TopicSubscription subscription = clientRule.topics().newSubscription(clientRule.getDefaultTopicName())
            .startAtHeadOfTopic()
            .handler(DO_NOTHING)
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        subscription.close();

        // then
        assertThat(subscription.isClosed()).isTrue();

        final long numCloseRequests = broker.getReceivedControlMessageRequests()
            .stream()
            .filter(r -> r.messageType() == ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .count();

        assertThat(numCloseRequests).isEqualTo(1); // did not attempt to close more than once
    }

    protected void assertMetadata(Event actualEvent, long expectedKey, long expectedPosition,
            TopicEventType expectedType)
    {

        final EventMetadata metadata = actualEvent.getMetadata();
        assertThat(metadata.getKey()).isEqualTo(expectedKey);
        assertThat(metadata.getPosition()).isEqualTo(expectedPosition);
        assertThat(metadata.getType()).isEqualTo(expectedType);
        assertThat(metadata.getTopicName()).isEqualTo(clientRule.getDefaultTopicName());
        assertThat(metadata.getPartitionId()).isEqualTo(clientRule.getDefaultPartitionId());
    }

    protected Stream<ExecuteCommandRequest> receivedSubscribeCommands()
    {
        return broker.getReceivedCommandRequests()
                .stream()
                .filter((e) -> e.eventType() == EventType.SUBSCRIBER_EVENT
                    && "SUBSCRIBE".equals(e.getCommand().get("state")));
    }

    private static class RecordingTopicEventHandler implements
        UniversalEventHandler,
        TaskEventHandler,
        WorkflowInstanceEventHandler,
        IncidentEventHandler,
        WorkflowEventHandler,
        RaftEventHandler
    {
        protected List<GeneralEvent> topicEvents = new CopyOnWriteArrayList<>();
        protected List<TaskEvent> taskEvents = new CopyOnWriteArrayList<>();
        protected List<WorkflowEvent> workflowEvents = new CopyOnWriteArrayList<>();
        protected List<WorkflowInstanceEvent> workflowInstanceEvents = new CopyOnWriteArrayList<>();
        protected List<IncidentEvent> incidentEvents = new CopyOnWriteArrayList<>();
        protected List<RaftEvent> raftEvents = new CopyOnWriteArrayList<>();

        @Override
        public void handle(GeneralEvent event) throws Exception
        {
            topicEvents.add(event);
        }

        @Override
        public void handle(TaskEvent event) throws Exception
        {
            taskEvents.add(event);
        }

        @Override
        public void handle(WorkflowInstanceEvent event) throws Exception
        {
            workflowInstanceEvents.add(event);
        }

        @Override
        public void handle(IncidentEvent event) throws Exception
        {
            incidentEvents.add(event);
        }

        @Override
        public void handle(WorkflowEvent event) throws Exception
        {
            workflowEvents.add(event);
        }

        @Override
        public void handle(RaftEvent event) throws Exception
        {
            raftEvents.add(event);
        }

        public int numTopicEvents()
        {
            return topicEvents.size();
        }
        public int numTaskEvents()
        {
            return taskEvents.size();
        }
        public int numWorkflowInstanceEvents()
        {
            return workflowInstanceEvents.size();
        }
        public int numWorkflowEvents()
        {
            return workflowEvents.size();
        }
        public int numIncidentEvents()
        {
            return incidentEvents.size();
        }
        public int numRaftEvents()
        {
            return raftEvents.size();
        }

    }
}
