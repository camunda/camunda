package io.zeebe.client.event;

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.test.util.TestUtil.waitUntil;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.impl.TopicSubscriptionImpl;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.brokerapi.ControlMessageRequest;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.test.util.Conditions;
import io.zeebe.test.util.TestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

import io.zeebe.transport.RemoteAddress;

public class TopicSubscriptionTest
{

    protected static final String SUBSCRIPTION_NAME = "foo";

    protected static final TopicEventHandler DO_NOTHING = (m, e) ->
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
        clientRule.topic().newSubscription()
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
            .containsEntry("eventType", "SUBSCRIBE")
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
        clientRule.topic().newSubscription()
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
        clientRule.topic().newSubscription()
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
            .containsEntry("eventType", "SUBSCRIBE")
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
        clientRule.topic().newSubscription()
            .startAtPosition(654L)
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
            .containsEntry("eventType", "SUBSCRIBE")
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
        clientRule.topic().newPollableSubscription()
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
            .containsEntry("eventType", "SUBSCRIBE")
            .containsEntry("startPosition", 0)
            .containsEntry("prefetchCapacity", 32)
            .containsEntry("name", SUBSCRIPTION_NAME)
            .doesNotContainEntry("forceStart", true);
    }

    @Test
    public void shouldValidateEventHandlerForManagedSubscription()
    {
        // given
        final TopicSubscriptionBuilder builder = clientRule.topic().newSubscription()
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
        final TopicSubscriptionBuilder builder = clientRule.topic().newSubscription()
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
        final PollableTopicSubscriptionBuilder builder = clientRule.topic().newPollableSubscription()
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
        final TopicSubscription subscription = clientRule.topic().newSubscription()
            .startAtHeadOfTopic()
            .handler(handler)
            .name(SUBSCRIPTION_NAME)
            .open();

        final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

        // when pushing two events
        broker.pushTopicEvent(clientAddress, 123L, 1L, 1L);
        broker.pushTopicEvent(clientAddress, 123L, 1L, 2L);

        // then
        TestUtil.waitUntil(() -> handler.numRecordedEvents() >= 3);
        assertThat(subscription.isOpen()).isFalse();
        Thread.sleep(1000L); // wait an extra second as we might receive more events if this feature is broken

        assertThat(handler.getRecordedEvents()).hasSize(3);

        final Set<Long> eventPositions = handler.getRecordedEvents().stream()
            .map((re) -> re.getMetadata().getEventPosition())
            .collect(Collectors.toSet());

        assertThat(eventPositions).containsExactly(1L);
    }

    @Test
    public void shouldResumeSubscriptionBeforeFailedEventAfterHandlerFailure()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final FailingHandler handler = new FailingHandler((m, e) -> m.getEventPosition() == 2L);
        final TopicSubscription subscription = clientRule.topic().newSubscription()
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
                .filter((c) -> "ACKNOWLEDGE".equals(c.getCommand().get("eventType")))
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
        assertThat(requests.contains(lastAck));
        assertThat(requests.indexOf(lastAck)).isLessThan(requests.indexOf(removeRequest));
    }

    @Test
    public void shouldContinueEventHandlingAfterSuccessfulRetry()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final AtomicInteger counter = new AtomicInteger(3);
        final FailingHandler handler = new FailingHandler((m, e) ->
                m.getEventPosition() == 1L &&
                counter.decrementAndGet() > 0);

        clientRule.topic().newSubscription()
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
                .anyMatch(re -> re.getMetadata().getEventPosition() == 2L));

        final List<Long> handledEventPositions = handler
            .getRecordedEvents()
            .stream()
            .map((re) -> re.getMetadata().getEventPosition())
            .collect(Collectors.toList());

        assertThat(handledEventPositions).containsExactly(1L, 1L, 1L, 2L);
    }

    @Test
    public void shouldSendPrefetchCapacityAsDefinedInClientProperties()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        // when
        clientRule.topic().newSubscription()
            .startAtHeadOfTopic()
            .handler(DO_NOTHING)
            .name(SUBSCRIPTION_NAME)
            .open();

        // then
        final ExecuteCommandRequest addSubscriptionRequest = broker.getReceivedCommandRequests().stream()
            .filter((r) -> r.eventType() == EventType.SUBSCRIBER_EVENT && "SUBSCRIBE".equals(r.getCommand().get("eventType")))
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

        final TopicSubscriptionImpl subscription = (TopicSubscriptionImpl) clientRule.topic().newSubscription()
            .startAtHeadOfTopic()
            .handler(handler)
            .name(SUBSCRIPTION_NAME)
            .open();

        final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

        broker.pushTopicEvent(clientAddress, 123L, 1L, 1L);
        TestUtil.waitUntil(() -> handler.isWaiting());

        // when
        final CompletableFuture<TopicSubscriptionImpl> closeFuture = subscription.closeAsync();

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
    public void shouldCloseSubscriptionOnClientDisconnect()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final TopicSubscriptionImpl subscription = (TopicSubscriptionImpl) clientRule.topic().newSubscription()
            .startAtHeadOfTopic()
            .handler(DO_NOTHING)
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        client.disconnect();

        // then
        assertThat(subscription.isClosed());
    }

    @Test
    public void shouldCloseSubscriptionOnChannelClose() throws InterruptedException
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final TopicSubscription subscription = clientRule.topic().newSubscription()
            .startAtHeadOfTopic()
            .handler(DO_NOTHING)
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        broker.closeTransport();

        // TODO: transport must determine faster that subscription open request does not succeed (including
        //  topology refreshes)
        Thread.sleep(10000L);

        // then
        TestUtil.waitUntil(() -> subscription.isClosed());
        assertThat(subscription.isClosed()).isTrue();
    }

    @Test
    public void shouldAllowReopeningSubscriptionAfterChannelClose() throws InterruptedException
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final TopicSubscription firstSubscription = clientRule.topic().newSubscription()
            .startAtHeadOfTopic()
            .handler(DO_NOTHING)
            .name(SUBSCRIPTION_NAME)
            .open();

        broker.closeTransport();
        TestUtil.waitUntil(() -> !firstSubscription.isOpen());
        client.disconnect();

        broker.bindTransport();
        client.connect();

        // when
        final TopicSubscription secondSubscription = clientRule.topic().newSubscription()
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

        clientRule.topic().newSubscription()
            .startAtHeadOfTopic()
            .handler(eventHandler)
            .taskEventHandler(eventHandler)
            .workflowInstanceEventHandler(eventHandler)
            .incidentEventHandler(eventHandler)
            .name(SUBSCRIPTION_NAME)
            .open();

        final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

        // when pushing two events
        broker.pushTopicEvent(clientAddress, 123L, 1L, 1L, EventType.RAFT_EVENT);
        broker.pushTopicEvent(clientAddress, 123L, 1L, 2L, EventType.RAFT_EVENT);

        // then
        waitUntil(() -> eventHandler.numTopicEvents == 2);

        assertThat(eventHandler.numTopicEvents).isEqualTo(2);
        assertThat(eventHandler.numTaskEvents).isEqualTo(0);
        assertThat(eventHandler.numWorkflowEvents).isEqualTo(0);
        assertThat(eventHandler.numIncidentEvents).isEqualTo(0);
    }

    @Test
    public void shouldInvokeTasktHandlerForTaskEvent()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final RecordingTopicEventHandler eventHandler = new RecordingTopicEventHandler();

        clientRule.topic().newSubscription()
            .startAtHeadOfTopic()
            .handler(eventHandler)
            .taskEventHandler(eventHandler)
            .workflowInstanceEventHandler(eventHandler)
            .incidentEventHandler(eventHandler)
            .name(SUBSCRIPTION_NAME)
            .open();

        final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

        // when pushing two events
        broker.pushTopicEvent(clientAddress, 123L, 1L, 1L, EventType.TASK_EVENT);
        broker.pushTopicEvent(clientAddress, 123L, 1L, 2L, EventType.TASK_EVENT);

        // then
        waitUntil(() -> eventHandler.numTaskEvents >= 2);

        assertThat(eventHandler.numTopicEvents).isEqualTo(0);
        assertThat(eventHandler.numTaskEvents).isEqualTo(2);
        assertThat(eventHandler.numWorkflowEvents).isEqualTo(0);
        assertThat(eventHandler.numIncidentEvents).isEqualTo(0);
    }

    @Test
    public void shouldInvokeWorkflowInstancetHandlerForWorkflowInstanceEvent()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final RecordingTopicEventHandler eventHandler = new RecordingTopicEventHandler();

        clientRule.topic().newSubscription()
            .startAtHeadOfTopic()
            .handler(eventHandler)
            .taskEventHandler(eventHandler)
            .workflowInstanceEventHandler(eventHandler)
            .incidentEventHandler(eventHandler)
            .name(SUBSCRIPTION_NAME)
            .open();

        final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

        // when pushing two events
        broker.pushTopicEvent(clientAddress, 123L, 1L, 1L, EventType.WORKFLOW_EVENT);
        broker.pushTopicEvent(clientAddress, 123L, 1L, 2L, EventType.WORKFLOW_EVENT);

        // then
        waitUntil(() -> eventHandler.numWorkflowEvents >= 2);

        assertThat(eventHandler.numTopicEvents).isEqualTo(0);
        assertThat(eventHandler.numTaskEvents).isEqualTo(0);
        assertThat(eventHandler.numWorkflowEvents).isEqualTo(2);
        assertThat(eventHandler.numIncidentEvents).isEqualTo(0);
    }

    @Test
    public void shouldInvokeIncidentEventHandlerForIncidentEvent()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final RecordingTopicEventHandler eventHandler = new RecordingTopicEventHandler();

        clientRule.topic().newSubscription()
            .startAtHeadOfTopic()
            .handler(eventHandler)
            .taskEventHandler(eventHandler)
            .workflowInstanceEventHandler(eventHandler)
            .incidentEventHandler(eventHandler)
            .name(SUBSCRIPTION_NAME)
            .open();

        final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

        // when pushing two events
        broker.pushTopicEvent(clientAddress, 123L, 1L, 1L, EventType.INCIDENT_EVENT);
        broker.pushTopicEvent(clientAddress, 123L, 1L, 2L, EventType.INCIDENT_EVENT);

        // then
        waitUntil(() -> eventHandler.numIncidentEvents >= 2);

        assertThat(eventHandler.numTopicEvents).isEqualTo(0);
        assertThat(eventHandler.numTaskEvents).isEqualTo(0);
        assertThat(eventHandler.numWorkflowEvents).isEqualTo(0);
        assertThat(eventHandler.numIncidentEvents).isEqualTo(2);
    }

    @Test
    public void shouldInvokeDefaultHandlerIfNoHandlerIsRegistered()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final RecordingTopicEventHandler defaultEventHandler = new RecordingTopicEventHandler();

        clientRule.topic().newSubscription()
            .startAtHeadOfTopic()
            .handler(defaultEventHandler)
            .name(SUBSCRIPTION_NAME)
            .open();

        final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

        // when pushing two events
        broker.pushTopicEvent(clientAddress, 123L, 1L, 1L, EventType.TASK_EVENT);
        broker.pushTopicEvent(clientAddress, 123L, 1L, 2L, EventType.WORKFLOW_EVENT);
        broker.pushTopicEvent(clientAddress, 123L, 1L, 3L, EventType.INCIDENT_EVENT);

        // then
        waitUntil(() -> defaultEventHandler.numTopicEvents == 3);

        assertThat(defaultEventHandler.numTopicEvents).isEqualTo(3);
    }

    public void shouldReopenSubscriptionOnChannelInterruption() throws InterruptedException
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        clientRule.topic().newSubscription()
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
            .containsEntry("eventType", "SUBSCRIBE")
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

        clientRule.topic().newSubscription()
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

        clientRule.topic().newSubscription()
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

    protected Stream<ExecuteCommandRequest> receivedSubscribeCommands()
    {
        return broker.getReceivedCommandRequests()
                .stream()
                .filter((e) -> e.eventType() == EventType.SUBSCRIBER_EVENT
                    && "SUBSCRIBE".equals(e.getCommand().get("eventType")));
    }

    private class RecordingTopicEventHandler implements TopicEventHandler, TaskEventHandler, WorkflowInstanceEventHandler, IncidentEventHandler
    {
        public int numTopicEvents = 0;
        public int numTaskEvents = 0;
        public int numWorkflowEvents = 0;
        public int numIncidentEvents = 0;

        @Override
        public void handle(EventMetadata metadata, TopicEvent event) throws Exception
        {
            numTopicEvents += 1;
        }

        @Override
        public void handle(EventMetadata metadata, TaskEvent event) throws Exception
        {
            numTaskEvents += 1;
        }

        @Override
        public void handle(EventMetadata metadata, WorkflowInstanceEvent event) throws Exception
        {
            numWorkflowEvents += 1;
        }

        @Override
        public void handle(EventMetadata metadata, IncidentEvent event) throws Exception
        {
            numIncidentEvents += 1;
        }
    }
}
