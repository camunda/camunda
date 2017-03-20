package org.camunda.tngp.broker.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.camunda.tngp.broker.protocol.clientapi.EmbeddedBrokerRule;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule;
import org.camunda.tngp.test.broker.protocol.clientapi.ControlMessageResponse;
import org.camunda.tngp.test.broker.protocol.clientapi.ExecuteCommandResponse;
import org.camunda.tngp.test.broker.protocol.clientapi.SubscribedEvent;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class TopicSubscriptionAcknowledgementTest
{
    protected static final String SUBSCRIPTION_NAME = "foo";

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule("tngp.unit-test.cfg.toml");
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    protected int subscriptionId;

    @Before
    public void openSubscription()
    {
        final ControlMessageResponse response = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicId", 0)
                .put("startPosition", 0)
                .put("name", SUBSCRIPTION_NAME)
                .done()
            .sendAndAwait();
        subscriptionId = (int) response.getData().get("id");
    }

    protected void closeSubscription()
    {
        apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicId", 0)
                .put("subscriptionId", subscriptionId)
                .done()
            .sendAndAwait();
    }

    @Test
    public void shouldAcknowledgePosition()
    {
        // when
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .eventTypeSubscription()
            .topicId(0)
            .command()
                .put("subscriptionName", SUBSCRIPTION_NAME)
                .put("event", "ACKNOWLEDGE")
                .put("ackPosition", 0)
                .done()
            .sendAndAwait();

        // then
        assertThat(response.getEvent()).containsEntry("subscriptionName", SUBSCRIPTION_NAME);
        assertThat(response.getEvent()).containsEntry("event", "ACKNOWLEDGED");
    }

    @Test
    public void shouldResumeAfterAcknowledgedPosition()
    {
        // given
        final List<SubscribedEvent> events = apiRule
                .subscribedEvents()
                .limit(2L)
                .collect(Collectors.toList());

        apiRule.createCmdRequest()
            .eventTypeSubscription()
            .topicId(0)
            .command()
                .put("subscriptionName", SUBSCRIPTION_NAME)
                .put("event", "ACKNOWLEDGE")
                .put("ackPosition", events.get(0).position())
                .done()
            .sendAndAwait();

        closeSubscription();

        apiRule.moveSubscribedEventsStreamToTail();

        // when
        openSubscription();

        // then
        final Optional<SubscribedEvent> firstEvent = apiRule
                .subscribedEvents()
                .findFirst();

        assertThat(firstEvent).isPresent();
        assertThat(firstEvent.get().position()).isEqualTo(events.get(1).position());
    }

    @Test
    @Ignore("https://github.com/camunda-tngp/camunda-tngp/issues/174")
    public void shouldResumeAtTailOnLongMaxAckPosition()
    {
        // given
        apiRule.createCmdRequest()
            .eventTypeSubscription()
            .command()
                .put("subscriptionName", SUBSCRIPTION_NAME)
                .put("event", "ACKNOWLEDGE")
                .put("acknowledgedPosition", Long.MAX_VALUE)
                .done()
            .sendAndAwait();

        closeSubscription();

        apiRule.moveSubscribedEventsStreamToTail();

        // when
        openSubscription();

        // then
        final Optional<SubscribedEvent> firstEvent = apiRule
                .subscribedEvents()
                .findFirst();

        assertThat(firstEvent).isPresent();
        // TODO: what is the expected behavior here?
    }

}
