package org.camunda.tngp.client.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.util.ClientRule;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.protocol.clientapi.SubscriptionType;
import org.camunda.tngp.test.broker.protocol.brokerapi.StubBrokerRule;
import org.camunda.tngp.test.util.TestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class TaskTopicSubscriptionTest
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
            .subscriberKey(subscriptionId)
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
        client.taskTopic(0).newSubscription()
            .startAtHeadOfTopic()
            .defaultHandler(handler)
            .name(SUBSCRIPTION_NAME)
            .open();

        final int clientChannelId = brokerRule.getReceivedCommandRequests().get(0).getChannelId();

        // when pushing two events
        pushTopicEvent(clientChannelId, 123L, 1L, 1L);
        pushTopicEvent(clientChannelId, 123L, 1L, 2L);

        // then
        TestUtil.waitUntil(() -> handler.numRecordedEvents() >= 3);
        Thread.sleep(1000L); // wait an extra second as we might receive more events if this feature is broken

        assertThat(handler.numRecordedEvents()).isEqualTo(3);

        final Set<Long> eventPositions = handler.getRecordedEvents().stream()
            .map((re) -> re.getMetadata().getEventPosition())
            .collect(Collectors.toSet());

        assertThat(eventPositions).containsExactly(1L);
    }

    @Test
    public void shouldCloseSubscriptionOnChannelClose()
    {
        // given
        brokerRule.stubTopicSubscriptionApi(123L);

        final TopicSubscription subscription = client.taskTopic(0).newSubscription()
            .startAtHeadOfTopic()
            .defaultHandler((m, t) ->
            {
            })
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        brokerRule.closeServerSocketBinding();

        // then
        TestUtil.waitUntil(() -> subscription.isClosed());
        assertThat(subscription.isClosed()).isTrue();
    }
}
