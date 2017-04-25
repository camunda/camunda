package org.camunda.tngp.broker.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.camunda.tngp.broker.protocol.clientapi.EmbeddedBrokerRule;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule;
import org.camunda.tngp.test.broker.protocol.clientapi.ExecuteCommandResponse;
import org.camunda.tngp.test.broker.protocol.clientapi.SubscribedEvent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class TopicSubscriptionAcknowledgementTest
{
    protected static final String SUBSCRIPTION_NAME = "foo";

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    protected long subscriberKey;

    @Before
    public void openSubscription()
    {
        openSubscription(0);
    }

    public void openSubscription(long startPosition)
    {
        final ExecuteCommandResponse response = apiRule
                .openTopicSubscription(0, SUBSCRIPTION_NAME, startPosition)
                .await();
        subscriberKey = response.key();
    }


    protected void closeSubscription()
    {
        apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicId", 0)
                .put("subscriberKey", subscriberKey)
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
                .put("name", SUBSCRIPTION_NAME)
                .put("eventType", "ACKNOWLEDGE")
                .put("ackPosition", 0)
                .done()
            .sendAndAwait();

        // then
        assertThat(response.getEvent()).containsEntry("name", SUBSCRIPTION_NAME);
        assertThat(response.getEvent()).containsEntry("eventType", "ACKNOWLEDGED");
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
                .put("name", SUBSCRIPTION_NAME)
                .put("eventType", "ACKNOWLEDGE")
                .put("ackPosition", events.get(0).position())
                .done()
            .sendAndAwait();

        closeSubscription();

        apiRule.moveMessageStreamToTail();

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
    public void shouldResumeAtTailOnLongMaxAckPosition()
    {
        // given
        apiRule.createCmdRequest()
            .eventTypeSubscription()
            .topicId(0)
            .command()
                .put("name", SUBSCRIPTION_NAME)
                .put("eventType", "ACKNOWLEDGE")
                .put("ackPosition", Long.MAX_VALUE)
                .done()
            .sendAndAwait();

        closeSubscription();

        apiRule.moveMessageStreamToTail();

        // when
        openSubscription();

        // and
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .topicId(0)
            .eventTypeTask()
            .command()
                .put("eventType", "CREATE")
                .put("type", "theTaskType")
                .done()
            .sendAndAwait();

        final long taskKey = response.key();

        // then
        final Optional<SubscribedEvent> firstEvent = apiRule
                .subscribedEvents()
                .findFirst();

        assertThat(firstEvent).isPresent();
        assertThat(firstEvent.get().key()).isEqualTo(taskKey);
    }

    @Test
    public void shouldPersistStartPosition()
    {
        // given
        apiRule.createCmdRequest()
            .topicId(0)
            .eventTypeTask()
            .command()
                .put("eventType", "CREATE")
                .put("type", "foo")
                .put("retries", 1)
                .done()
            .sendAndAwait();

        final List<Long> taskEventPositions = apiRule.subscribedEvents()
            .filter((e) -> e.eventType() == EventType.TASK_EVENT)
            .map((e) -> e.position())
            .limit(2)
            .collect(Collectors.toList());

        closeSubscription();
        apiRule.moveMessageStreamToTail();

        // when
        openSubscription(taskEventPositions.get(1));

        // then it begins at the original offset (we didn't send any ACK before)
        final List<Long> taskEventPositionsAfterReopen = apiRule.subscribedEvents()
            .filter((e) -> e.eventType() == EventType.TASK_EVENT)
            .map((e) -> e.position())
            .limit(2)
            .collect(Collectors.toList());

        assertThat(taskEventPositionsAfterReopen).containsExactlyElementsOf(taskEventPositions);
    }

}
