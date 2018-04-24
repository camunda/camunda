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

import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.impl.TopicSubscriberGroup;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.task.impl.subscription.Subscriber;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandResponseBuilder;
import io.zeebe.test.broker.protocol.brokerapi.ResponseController;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.RemoteAddress;

public class PollableTopicSubscriptionTest
{

    protected static final UniversalEventHandler DO_NOTHING = e ->
    { };

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
    public void shouldOpenSubscriptionAndForceStart()
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        // when
        clientRule.getClient().topics().newPollableSubscription(clientRule.getDefaultTopicName())
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
        final PollableTopicSubscription subscription = clientRule.topics().newPollableSubscription(clientRule.getDefaultTopicName())
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
            assertThat(e.getCause()).isInstanceOf(RuntimeException.class);
            assertThat(e.getCause()).hasMessageContaining("Exception during handling of event");
        }

        assertThat(subscription.isOpen()).isTrue();
        assertThat(handler.numRecordedEvents()).isEqualTo(1);
    }

    @Test
    public void shouldCloseSubscriptionOnChannelClose() throws InterruptedException
    {
        // given
        broker.stubTopicSubscriptionApi(123L);

        final PollableTopicSubscription subscription = clientRule.topics().newPollableSubscription(clientRule.getDefaultTopicName())
            .startAtHeadOfTopic()
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        broker.closeTransport();
        Thread.sleep(500L); // ensuring a reconnection attempt
        clientRule.getClock().addTime(Duration.ofSeconds(60)); // let request time out immediately

        // then
        TestUtil.waitUntil(() -> subscription.isClosed());
        assertThat(subscription.isClosed()).isTrue();
    }

    @Test
    public void shouldAcknowledgeOnlyOnceWhenTriggeredMultipleTimes() throws InterruptedException
    {
        // given
        final long subscriberKey = 123L;
        broker.stubTopicSubscriptionApi(subscriberKey);

        final ResponseController ackResponseController = stubAcknowledgeRequest()
            .registerControlled();

        final PollableTopicSubscription subscription = clientRule.topics().newPollableSubscription(clientRule.getDefaultTopicName())
            .startAtHeadOfTopic()
            .name(SUBSCRIPTION_NAME)
            .open();

        final int subscriptionCapacity = ((ZeebeClientImpl) client).getSubscriptionPrefetchCapacity();
        final int replenishmentThreshold = (int) (subscriptionCapacity * (1.0d - Subscriber.REPLENISHMENT_THRESHOLD));

        final RemoteAddress clientAddress = receivedSubscribeCommands().findFirst().get().getSource();

        // push and handle as many events such that an ACK is triggered
        for (int i = 0; i < replenishmentThreshold + 1; i++)
        {
            broker.pushTopicEvent(clientAddress, subscriberKey, i, i);
        }
        final AtomicInteger handledEvents = new AtomicInteger(0);
        doRepeatedly(() -> handledEvents.addAndGet(subscription.poll(DO_NOTHING))).until(e -> e == replenishmentThreshold + 1);

        waitUntil(() -> receivedAckRequests().count() == 1);

        // when consuming another event (while the ACK is not yet confirmed)
        broker.pushTopicEvent(clientAddress, subscriberKey, 99, 99);
        doRepeatedly(() -> subscription.poll(DO_NOTHING)).until(e -> e == 1);

        // then
        Thread.sleep(500L); // give some time for another ACK request
        ackResponseController.unblockNextResponse();

        final List<ExecuteCommandRequest> ackRequests = receivedAckRequests()
            .collect(Collectors.toList());

        assertThat(ackRequests).hasSize(1); // and not two

        stubAcknowledgeRequest().register(); // unblock the request so tear down succeeds
    }

    @Test
    public void shouldPollEventsWhileModifyingSubscribers() throws InterruptedException
    {
        // given
        final int subscriberKey = 456;
        broker.stubTopicSubscriptionApi(subscriberKey);

        final ControllableHandler handler = new ControllableHandler();

        final TopicSubscriberGroup subscription = (TopicSubscriberGroup) client.topics()
            .newPollableSubscription(clientRule.getDefaultTopicName())
            .name("hohoho")
            .open();

        final RemoteAddress clientAddress = broker.getReceivedCommandRequests().get(0).getSource();
        broker.pushTopicEvent(clientAddress, subscriberKey, 1, 1);

        waitUntil(() -> subscription.size() == 1); // event is received

        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread poller = new Thread(() -> subscription.poll(handler));
        poller.setUncaughtExceptionHandler((thread, throwable) -> failure.set(throwable));
        poller.start();

        waitUntil(() -> handler.isWaiting());

        // closing the subscriber, triggering reopen request
        broker.closeTransport();

        waitUntil(() -> subscription.numActiveSubscribers() == 0);

        // when continuing event handling
        handler.disableWait();
        handler.signal();

        // then the concurrent modification of subscribers did not affect the poller
        poller.join(Duration.ofSeconds(10).toMillis());
        assertThat(failure.get()).isNull();

        // make the reopen request time out immediately so
        // that the client can close without waiting for the timeout
        clientRule.getClock().addTime(Duration.ofSeconds(60));


    }

    private Stream<ExecuteCommandRequest> receivedAckRequests()
    {
        return broker.getReceivedCommandRequests().stream()
                .filter((c) -> c.eventType() == EventType.SUBSCRIPTION_EVENT)
                .filter((c) -> "ACKNOWLEDGE".equals(c.getCommand().get("state")));
    }

    protected Stream<ExecuteCommandRequest> receivedSubscribeCommands()
    {
        return broker.getReceivedCommandRequests()
                .stream()
                .filter((e) -> e.eventType() == EventType.SUBSCRIBER_EVENT
                    && "SUBSCRIBE".equals(e.getCommand().get("state")));
    }

    private ExecuteCommandResponseBuilder stubAcknowledgeRequest()
    {
        return broker.onExecuteCommandRequest(EventType.SUBSCRIPTION_EVENT, "ACKNOWLEDGE")
            .respondWith()
            .key(r -> r.key())
            .value()
                .allOf((r) -> r.getCommand())
                .put("state", "ACKNOWLEDGED")
                .done();
    }
}
