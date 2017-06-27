package io.zeebe.client.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.test.util.TestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.transport.RemoteAddress;

public class PollableTopicSubscriptionTest
{

    protected static final String SUBSCRIPTION_NAME = "foo";

    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule broker = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(broker).around(clientRule);

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
    public void shouldOpenSubscriptionAndForceStart()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        // when
        clientRule.topic().newPollableSubscription()
            .startAtHeadOfTopic()
            .forcedStart()
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

    /**
     * Exception handling should be left to the client for pollable subscriptions
     */
    @Test
    public void shouldNotRetryOnHandlerFailure() throws InterruptedException
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final FailingHandler handler = new FailingHandler();
        final PollableTopicSubscription subscription = clientRule.topic().newPollableSubscription()
            .startAtHeadOfTopic()
            .name(SUBSCRIPTION_NAME)
            .open();

        final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();

        broker.pushTopicEvent(clientAddress, 123L, 1L, 1L);
        broker.pushTopicEvent(clientAddress, 123L, 1L, 2L);

        // when
        try
        {
            TestUtil.doRepeatedly(() -> subscription.poll(handler)).until((i) -> false, (e) -> e != null);
            fail("Exception expected");
        }
        catch (Exception e)
        {
            // then
            assertThat(e).isInstanceOf(RuntimeException.class);
            assertThat(e).hasMessageContaining("Exception during handling of event");
        }

        assertThat(subscription.isOpen()).isTrue();
        assertThat(handler.numRecordedEvents()).isEqualTo(1);
    }

    @Test
    public void shouldCloseSubscriptionOnChannelClose() throws InterruptedException
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final PollableTopicSubscription subscription = clientRule.topic().newPollableSubscription()
            .startAtHeadOfTopic()
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        broker.closeTransport();

        // TODO:
        // * reopening the subscription takes a rather long time (up to 5 topology refreshes, which is slow when the remote is not reachable)
        // * transport must cancel requests faster
        Thread.sleep(10000L);

        // then
        TestUtil.waitUntil(() -> subscription.isClosed());
        assertThat(subscription.isClosed()).isTrue();
    }
}
