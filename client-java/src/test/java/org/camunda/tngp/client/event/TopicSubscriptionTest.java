package org.camunda.tngp.client.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.test.util.TestUtil.waitUntil;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.event.impl.TopicSubscriptionImpl;
import org.camunda.tngp.client.util.ClientRule;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.test.broker.protocol.brokerapi.ControlMessageRequest;
import org.camunda.tngp.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import org.camunda.tngp.test.broker.protocol.brokerapi.StubBrokerRule;
import org.camunda.tngp.test.util.TestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class TopicSubscriptionTest
{

    protected static final String SUBSCRIPTION_NAME = "foo";

    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule broker = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(broker).around(clientRule);

    protected TngpClient client;

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

        final TopicEventHandler noOpHandler = (m, e) ->
        { };

        // when
        clientRule.topic().newSubscription()
            .startAtHeadOfTopic()
            .handler(noOpHandler)
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

        final TopicEventHandler noOpHandler = (m, e) ->
        { };

        // when
        clientRule.topic().newSubscription()
            .startAtHeadOfTopic()
            .forcedStart()
            .handler(noOpHandler)
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

        final int clientChannelId = broker.getReceivedCommandRequests().get(0).getChannelId();

        // when pushing two events
        broker.pushTopicEvent(clientChannelId, 123L, 1L, 1L);
        broker.pushTopicEvent(clientChannelId, 123L, 1L, 2L);

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

        final int clientChannelId = broker.getReceivedCommandRequests().get(0).getChannelId();
        broker.pushTopicEvent(clientChannelId, 123L, 1L, 1L);

        // when
        broker.pushTopicEvent(clientChannelId, 123L, 1L, 2L);

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

        final int clientChannelId = broker.getReceivedCommandRequests().get(0).getChannelId();
        broker.pushTopicEvent(clientChannelId, 123L, 1L, 1L);

        // when
        broker.pushTopicEvent(clientChannelId, 123L, 1L, 2L);

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
            .handler((m, e) ->
            {

            })
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

        final int clientChannelId = broker.getReceivedCommandRequests().get(0).getChannelId();

        broker.pushTopicEvent(clientChannelId, 123L, 1L, 1L);
        TestUtil.waitUntil(() -> handler.isWaiting());

        // when
        final CompletableFuture<Void> closeFuture = subscription.closeAsync();

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
            .handler((m, e) ->
            {
            })
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        clientRule.closeClient();

        // then
        assertThat(subscription.isClosed());
    }

    @Test
    public void shouldCloseSubscriptionOnChannelClose()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final TopicSubscription subscription = clientRule.topic().newSubscription()
            .startAtHeadOfTopic()
            .handler((m, e) ->
            {
            })
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        broker.closeServerSocketBinding();

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
            .handler((m, e) ->
            {
            })
            .name(SUBSCRIPTION_NAME)
            .open();

        broker.closeServerSocketBinding();
        TestUtil.waitUntil(() -> !firstSubscription.isOpen());
        clientRule.closeClient();

        broker.openServerSocketBinding();
        client = clientRule.getClient();

        // when
        final TopicSubscription secondSubscription = clientRule.topic().newSubscription()
                .startAtHeadOfTopic()
                .handler((m, e) ->
                {
                })
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
            .name(SUBSCRIPTION_NAME)
            .open();

        final int clientChannelId = broker.getReceivedCommandRequests().get(0).getChannelId();

        // when pushing two events
        broker.pushTopicEvent(clientChannelId, 123L, 1L, 1L, EventType.RAFT_EVENT);
        broker.pushTopicEvent(clientChannelId, 123L, 1L, 2L, EventType.RAFT_EVENT);

        // then
        waitUntil(() -> eventHandler.numTopicEvents == 2);

        assertThat(eventHandler.numTopicEvents).isEqualTo(2);
        assertThat(eventHandler.numTaskEvents).isEqualTo(0);
        assertThat(eventHandler.numWorkflowEvents).isEqualTo(0);
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
            .name(SUBSCRIPTION_NAME)
            .open();

        final int clientChannelId = broker.getReceivedCommandRequests().get(0).getChannelId();

        // when pushing two events
        broker.pushTopicEvent(clientChannelId, 123L, 1L, 1L, EventType.TASK_EVENT);
        broker.pushTopicEvent(clientChannelId, 123L, 1L, 2L, EventType.TASK_EVENT);

        // then
        waitUntil(() -> eventHandler.numTaskEvents >= 2);

        assertThat(eventHandler.numTopicEvents).isEqualTo(0);
        assertThat(eventHandler.numTaskEvents).isEqualTo(2);
        assertThat(eventHandler.numWorkflowEvents).isEqualTo(0);
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
            .name(SUBSCRIPTION_NAME)
            .open();

        final int clientChannelId = broker.getReceivedCommandRequests().get(0).getChannelId();

        // when pushing two events
        broker.pushTopicEvent(clientChannelId, 123L, 1L, 1L, EventType.WORKFLOW_EVENT);
        broker.pushTopicEvent(clientChannelId, 123L, 1L, 2L, EventType.WORKFLOW_EVENT);

        // then
        waitUntil(() -> eventHandler.numWorkflowEvents >= 2);

        assertThat(eventHandler.numTopicEvents).isEqualTo(0);
        assertThat(eventHandler.numTaskEvents).isEqualTo(0);
        assertThat(eventHandler.numWorkflowEvents).isEqualTo(2);
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

        final int clientChannelId = broker.getReceivedCommandRequests().get(0).getChannelId();

        // when pushing two events
        broker.pushTopicEvent(clientChannelId, 123L, 1L, 1L, EventType.TASK_EVENT);
        broker.pushTopicEvent(clientChannelId, 123L, 1L, 2L, EventType.WORKFLOW_EVENT);

        // then
        waitUntil(() -> defaultEventHandler.numTopicEvents == 2);

        assertThat(defaultEventHandler.numTopicEvents).isEqualTo(2);
    }

    private class RecordingTopicEventHandler implements TopicEventHandler, TaskEventHandler, WorkflowInstanceEventHandler
    {
        public int numTopicEvents = 0;
        public int numTaskEvents = 0;
        public int numWorkflowEvents = 0;

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
    }
}
