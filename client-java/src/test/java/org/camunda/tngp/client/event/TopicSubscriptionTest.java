package org.camunda.tngp.client.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.util.ClientRule;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.protocol.clientapi.SubscriptionType;
import org.camunda.tngp.test.broker.protocol.brokerapi.ControlMessageRequest;
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
    public StubBrokerRule brokerRule = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

    protected TngpClient client;

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();
    }

    protected void pushTopicEvent(int channelId, long subscriptionId, long key, long position)
    {
        brokerRule.newSubscribedEvent()
            .topicId(0)
            .longKey(key)
            .position(position)
            .eventType(EventType.RAFT_EVENT)
            .subscriptionId(subscriptionId)
            .subscriptionType(SubscriptionType.TOPIC_SUBSCRIPTION)
            .event()
                .done()
            .push(channelId);
    }

    @Test
    public void shouldRetryThreeTimesOnHandlerFailure() throws InterruptedException
    {
        // given
        brokerRule.stubTopicSubscriptionApi(123L);

        final FailingHandler handler = new FailingHandler();
        client.topic(0).newSubscription()
            .startAtHeadOfTopic()
            .handler(handler)
            .name(SUBSCRIPTION_NAME)
            .open();

        final int clientChannelId = brokerRule.getReceivedControlMessageRequests().get(0).getChannelId();

        // when pushing two events
        pushTopicEvent(clientChannelId, 123L, 1L, 1L);
        pushTopicEvent(clientChannelId, 123L, 1L, 2L);

        // then
        TestUtil.waitUntil(() -> handler.numRecordedEvents() >= 3);
        Thread.sleep(1000L); // wait an extra second as we might receive more events if this feature is broken

        assertThat(handler.getRecordedEvents()).hasSize(3);

        final Set<Long> eventPositions = handler.getRecordedEvents().stream()
            .map((re) -> re.getMetadata().getEventPosition())
            .collect(Collectors.toSet());

        assertThat(eventPositions).containsExactly(1L);
    }

    @Test
    public void shouldCloseSubscriptionOnThirdHandlerFailure()
    {
        // given
        brokerRule.stubTopicSubscriptionApi(123L);

        final FailingHandler handler = new FailingHandler();
        final TopicSubscription subscription = client.topic(0).newSubscription()
            .startAtHeadOfTopic()
            .handler(handler)
            .name(SUBSCRIPTION_NAME)
            .open();

        final int clientChannelId = brokerRule.getReceivedControlMessageRequests().get(0).getChannelId();

        // when
        pushTopicEvent(clientChannelId, 123L, 1L, 1L);

        // then
        TestUtil.waitUntil(() -> handler.numRecordedEvents() >= 3);
        assertThat(subscription.isOpen()).isFalse();

        final List<ControlMessageRequest> controlMessageRequests = brokerRule.getReceivedControlMessageRequests();
        assertThat(controlMessageRequests).hasSize(2);
        assertThat(controlMessageRequests.get(1).messageType()).isEqualTo(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION);
        assertThat(controlMessageRequests.get(1).getData()).containsExactly(entry("subscriptionId", 123));

    }

    @Test
    public void shouldResumeSubscriptionBeforeFailedEventAfterHandlerFailure()
    {
        // given
        brokerRule.stubTopicSubscriptionApi(123L);

        final FailingHandler handler = new FailingHandler((m, e) -> m.getEventPosition() == 2L);
        final TopicSubscription subscription = client.topic(0).newSubscription()
                .startAtHeadOfTopic()
                .handler(handler)
                .name(SUBSCRIPTION_NAME)
                .open();

        final int clientChannelId = brokerRule.getReceivedControlMessageRequests().get(0).getChannelId();
        pushTopicEvent(clientChannelId, 123L, 1L, 1L);

        // when
        pushTopicEvent(clientChannelId, 123L, 1L, 2L);

        // then
        TestUtil.waitUntil(() -> subscription.isClosed());

        final List<ControlMessageRequest> controlMessageRequests = brokerRule.getReceivedControlMessageRequests();

        final List<ControlMessageRequest> acknowledgements = controlMessageRequests.stream()
                .filter((c) -> c.messageType() == ControlMessageType.ACKNOWLEDGE_TOPIC_EVENT)
                .collect(Collectors.toList());

        assertThat(acknowledgements).isNotEmpty();

        final ControlMessageRequest lastAck = acknowledgements.get(acknowledgements.size() - 1);
        assertThat(lastAck.getData().get("subscriptionId")).isEqualTo(123);
        assertThat(lastAck.getData().get("acknowledgedPosition")).isEqualTo(1);

        final ControlMessageRequest removeRequest = controlMessageRequests.stream()
                .filter((c) -> c.messageType() == ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
                .findFirst()
                .get();

        assertThat(controlMessageRequests.indexOf(lastAck)).isLessThan(controlMessageRequests.indexOf(removeRequest));
    }

    @Test
    public void shouldContinueEventHandlingAfterSuccessfulRetry()
    {
        // given
        brokerRule.stubTopicSubscriptionApi(123L);

        final AtomicInteger counter = new AtomicInteger(3);
        final FailingHandler handler = new FailingHandler((m, e) ->
                m.getEventPosition() == 1L &&
                counter.decrementAndGet() > 0);

        client.topic(0).newSubscription()
            .startAtHeadOfTopic()
            .handler(handler)
            .name(SUBSCRIPTION_NAME)
            .open();

        final int clientChannelId = brokerRule.getReceivedControlMessageRequests().get(0).getChannelId();
        pushTopicEvent(clientChannelId, 123L, 1L, 1L);

        // when
        pushTopicEvent(clientChannelId, 123L, 1L, 2L);

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
}
