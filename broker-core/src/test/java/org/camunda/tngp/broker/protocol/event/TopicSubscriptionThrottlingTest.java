package org.camunda.tngp.broker.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;

import org.camunda.tngp.broker.protocol.clientapi.EmbeddedBrokerRule;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule;
import org.camunda.tngp.test.broker.protocol.clientapi.ControlMessageResponse;
import org.camunda.tngp.test.util.TestUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class TopicSubscriptionThrottlingTest
{

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule("tngp.unit-test.cfg.toml");
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    protected int subscriptionId;

    public void openSubscription(int prefetchCapacity)
    {
        final ControlMessageResponse response = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicId", 0)
                .put("startPosition", 0)
                .put("name", "foo")
                .put("prefetchCapacity", prefetchCapacity)
                .done()
            .sendAndAwait();
        subscriptionId = (int) response.getData().get("id");
    }

    protected void closeSubscription()
    {
        apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .data()
                .put("subscriptionId", subscriptionId)
                .done()
            .sendAndAwait();
    }

    @Test
    public void shouldNotPushMoreThanPrefetchCapacity() throws InterruptedException
    {
        // given
        final int nrOfTasks = 5;
        final int prefetchCapacity = 3;

        createTasks(nrOfTasks);

        // when
        openSubscription(prefetchCapacity);

        // then
        TestUtil.waitUntil(() -> apiRule.numSubscribedEventsAvailable() >= 3);
        Thread.sleep(1000L); // there might be more received in case this feature is broken
        assertThat(apiRule.numSubscribedEventsAvailable()).isEqualTo(prefetchCapacity);

    }

    @Test
    public void shouldPushMoreAfterAck() throws InterruptedException
    {
        // given
        final int nrOfTasks = 5;
        final int prefetchCapacity = 3;

        createTasks(nrOfTasks);
        openSubscription(prefetchCapacity);
        TestUtil.waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 3);

        final List<Long> eventPositions = apiRule.subscribedEvents()
                .limit(3)
                .map((e) -> e.position())
                .collect(Collectors.toList());

        apiRule.moveSubscribedEventsStreamToTail();

        // when
        apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.ACKNOWLEDGE_TOPIC_EVENT)
                .data()
                .put("subscriptionId", subscriptionId)
                .put("acknowledgedPosition", eventPositions.get(1))
                .done()
            .sendAndAwait();

        // then
        Thread.sleep(1000L); // there might be more received in case this feature is broken
        assertThat(apiRule.numSubscribedEventsAvailable()).isEqualTo(2);

        final List<Long> eventPositionsAfterAck = apiRule.subscribedEvents()
                .limit(2)
                .map((e) -> e.position())
                .collect(Collectors.toList());

        assertThat(eventPositionsAfterAck.get(0)).isGreaterThan(eventPositions.get(2));
        assertThat(eventPositionsAfterAck.get(1)).isGreaterThan(eventPositions.get(2));
    }

    @Test
    public void shouldPushAllEventsWithoutPrefetchCapacity() throws InterruptedException
    {
        // given
        final int nrOfTasks = 5;
        final int prefetchCapacity = -1;

        createTasks(nrOfTasks);

        // when
        openSubscription(prefetchCapacity);

        // then
        final int expectedNumberOfEvents =
                nrOfTasks * 2 + // CREATE and CREATED
                2; // two raft events

        TestUtil.waitUntil(() -> apiRule.numSubscribedEventsAvailable() == expectedNumberOfEvents);
    }

    protected void createTasks(int nrOfTasks)
    {
        for (int i = 0; i < nrOfTasks; i++)
        {
            apiRule.createCmdRequest()
                .topicId(0)
                .eventTypeTask()
                .command()
                    .put("eventType", "CREATE")
                    .put("type", "theTaskType")
                    .done()
                .sendAndAwait();
        }

    }
}
