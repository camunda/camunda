package org.camunda.tngp.broker.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.camunda.tngp.broker.protocol.clientapi.EmbeddedBrokerRule;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.protocol.clientapi.ErrorCode;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.protocol.clientapi.SubscriptionType;
import org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule;
import org.camunda.tngp.test.broker.protocol.clientapi.ControlMessageResponse;
import org.camunda.tngp.test.broker.protocol.clientapi.ErrorResponse;
import org.camunda.tngp.test.broker.protocol.clientapi.ExecuteCommandResponse;
import org.camunda.tngp.test.broker.protocol.clientapi.SubscribedEvent;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class TopicSubscriptionTest
{

    public static final int MAXIMUM_SUBSCRIPTION_NAME_LENGTH = 32;

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule("tngp.unit-test.cfg.toml");
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    @Test
    public void shouldOpenSubscription()
    {
        // when
        final ControlMessageResponse subscriptionResponse = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicId", 0)
                .put("startPosition", 0)
                .put("name", "foo")
                .done()
            .sendAndAwait();

        // then
        assertThat((int) subscriptionResponse.getData().get("id")).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void shouldCloseSubscription()
    {
        // given
        final ControlMessageResponse addResponse = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicId", 0)
                .put("startPosition", 0)
                .put("name", "foo")
                .done()
            .sendAndAwait();
        final int subscriptionId = (int) addResponse.getData().get("id");

        // when
        final ControlMessageResponse removeResponse = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicId", 0)
                .put("subscriptionId", subscriptionId)
                .done()
            .sendAndAwait();

        // then
        assertThat(removeResponse.getData()).containsExactly(entry("subscriptionId", subscriptionId), entry("topicId", 0));
    }

    @Test
    public void shouldNotPushEventsAfterClose() throws InterruptedException
    {
        // given
        final ControlMessageResponse addResponse = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicId", 0)
                .put("startPosition", 0)
                .put("name", "foo")
                .done()
            .sendAndAwait();
        final int subscriptionId = (int) addResponse.getData().get("id");

        apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicId", 0)
                .put("subscriptionId", subscriptionId)
                .done()
            .sendAndAwait();

        apiRule.moveSubscribedEventsStreamToTail();

        // when creating a task
        apiRule.createCmdRequest()
            .topicId(0)
            .eventTypeTask()
            .command()
                .put("event", "CREATE")
                .put("type", "theTaskType")
                .done()
            .sendAndAwait();

        // then no events are received
        Thread.sleep(1000L);
        assertThat(apiRule.numSubscribedEventsAvailable()).isEqualTo(0);
    }

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
        final int subscriptionId = (int) subscriptionResponse.getData().get("id");

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

    @Test
    public void shouldNotOpenSubscriptionForNonExistingTopic()
    {
        // when
        final ErrorResponse errorResponse = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicId", Integer.MAX_VALUE)
                .put("startPosition", 0)
                .put("name", "foo")
                .done()
            .send().awaitError();

        // then
        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorResponse.getErrorData()).isEqualTo("Cannot open topic subscription. No subscription management " +
                "processor registered for topic " + Integer.MAX_VALUE);
        assertThat(errorResponse.getFailedRequest().get("topicId")).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    public void shouldNotCloseSubscriptionForNonExistingTopic()
    {
        // when
        final ErrorResponse errorResponse = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicId", Integer.MAX_VALUE)
                .put("subscriptionId", 0L)
                .done()
            .send().awaitError();

        // then
        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorResponse.getErrorData()).isEqualTo("Cannot close topic subscription. No subscription management " +
                "processor registered for topic " + Integer.MAX_VALUE);
        assertThat(errorResponse.getFailedRequest().get("topicId")).isEqualTo(Integer.MAX_VALUE);
    }


    @Test
    public void shouldNotCloseSubscriptionNonExistingSubscription()
    {
        // when
        final ErrorResponse errorResponse = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicId", 0)
                .put("subscriptionId", Long.MAX_VALUE)
                .done()
            .send().awaitError();

        // then
        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorResponse.getErrorData()).isEqualTo("Cannot close topic subscription. Subscription with id " +
                Long.MAX_VALUE + " is not open");
        assertThat(errorResponse.getFailedRequest().get("subscriptionId")).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    public void shouldOpenSubscriptionWithMaximumNameLength()
    {
        // when
        final ControlMessageResponse response = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicId", 0)
                .put("startPosition", 0)
                .put("name", getStringOfLength(MAXIMUM_SUBSCRIPTION_NAME_LENGTH))
                .done()
            .sendAndAwait();

        // then
        assertThat((int) response.getData().get("id")).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void shouldNotOpenSubscriptionWithOverlongName()
    {
        // when
        final ErrorResponse errorResponse = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicId", 0)
                .put("startPosition", 0)
                .put("name", getStringOfLength(MAXIMUM_SUBSCRIPTION_NAME_LENGTH + 1))
                .done()
            .send().awaitError();

        // then
        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorResponse.getErrorData()).isEqualTo("Cannot open topic subscription. Subscription name must be " +
                MAXIMUM_SUBSCRIPTION_NAME_LENGTH + " characters or shorter.");
    }


    protected String getStringOfLength(int numCharacters)
    {
        final char[] characters = new char[numCharacters];
        Arrays.fill(characters, 'a');
        return new String(characters);
    }

}
