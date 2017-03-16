package org.camunda.tngp.client.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

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

public class PollableTopicSubscriptionTest
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

    /**
     * Exception handling should be left to the client for pollable subscriptions
     */
    @Test
    public void shouldNotRetryOnHandlerFailure() throws InterruptedException
    {
        // given
        brokerRule.stubTopicSubscriptionApi(123L);

        final FailingHandler handler = new FailingHandler();
        final PollableTopicSubscription subscription = client.topic(0).newPollableSubscription()
            .startAtHeadOfTopic()
            .name(SUBSCRIPTION_NAME)
            .open();

        final int clientChannelId = brokerRule.getReceivedControlMessageRequests().get(0).getChannelId();

        pushTopicEvent(clientChannelId, 123L, 1L, 1L);
        pushTopicEvent(clientChannelId, 123L, 1L, 2L);

        // when
        try
        {
            TestUtil.doRepeatedly(() -> subscription.poll(handler)).until((i) -> false, (e) -> e != null);
            fail("Exception expected");
        }
        catch (Exception e)
        {
            // then
            // TODO: assert exception
        }

        assertThat(subscription.isOpen()).isTrue();
        assertThat(handler.numRecordedEvents()).isEqualTo(1);
    }
}
