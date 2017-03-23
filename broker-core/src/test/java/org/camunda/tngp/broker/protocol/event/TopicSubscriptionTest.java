package org.camunda.tngp.broker.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import java.util.stream.Collectors;

import org.camunda.tngp.broker.protocol.clientapi.EmbeddedBrokerRule;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.protocol.clientapi.SubscriptionType;
import org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule;
import org.camunda.tngp.test.broker.protocol.clientapi.ControlMessageResponse;
import org.camunda.tngp.test.broker.protocol.clientapi.ExecuteCommandResponse;
import org.camunda.tngp.test.broker.protocol.clientapi.SubscribedEvent;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class TopicSubscriptionTest
{

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule("tngp.unit-test.cfg.toml");
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    protected int subscriptionId;

    @Test
    public void shouldPushEvents()
    {
        // given
        final ExecuteCommandResponse createTaskResponse = apiRule.createCmdRequest()
            .topicId(0)
            .eventTypeTask()
            .command()
                .put("eventType", "CREATE")
                .put("type", "foo")
                .put("retries", 1)
                .done()
            .sendAndAwait();
        final long taskKey = createTaskResponse.key();

        // when
        final ControlMessageResponse subscriptionResponse = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicId", 0)
                .put("startPosition", 0)
                .put("name", "foo")
                .done()
            .sendAndAwait();
        subscriptionId = (int) subscriptionResponse.getData().get("id");

        // then
        final List<SubscribedEvent> taskEvents = apiRule.subscribedEvents()
            .filter((e) -> e.eventType() == EventType.TASK_EVENT)
            .limit(2)
            .collect(Collectors.toList());

        assertThat(taskEvents).hasSize(2);
        assertThat(taskEvents.get(0).subscriptionId()).isEqualTo(subscriptionId);
        assertThat(taskEvents.get(0).subscriptionType()).isEqualTo(SubscriptionType.TOPIC_SUBSCRIPTION);
        assertThat(taskEvents.get(0).position()).isEqualTo(taskKey);
        assertThat(taskEvents.get(0).topicId()).isEqualTo(0);
        assertThat(taskEvents.get(0).event()).contains(entry("eventType", "CREATE"));

        assertThat(taskEvents.get(1).subscriptionId()).isEqualTo(subscriptionId);
        assertThat(taskEvents.get(1).subscriptionType()).isEqualTo(SubscriptionType.TOPIC_SUBSCRIPTION);
        assertThat(taskEvents.get(1).position()).isGreaterThan(taskKey);
        assertThat(taskEvents.get(1).topicId()).isEqualTo(0);
        assertThat(taskEvents.get(1).event()).contains(entry("eventType", "CREATED"));
    }
}
