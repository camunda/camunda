package org.camunda.tngp.broker.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.camunda.tngp.broker.protocol.clientapi.EmbeddedBrokerRule;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.protocol.clientapi.ErrorCode;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.protocol.clientapi.SubscriptionType;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule;
import org.camunda.tngp.test.broker.protocol.clientapi.ControlMessageResponse;
import org.camunda.tngp.test.broker.protocol.clientapi.ErrorResponse;
import org.camunda.tngp.test.broker.protocol.clientapi.ExecuteCommandResponse;
import org.camunda.tngp.test.broker.protocol.clientapi.SubscribedEvent;
import org.camunda.tngp.test.util.TestUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class TopicSubscriptionTest
{

    public static final int MAXIMUM_SUBSCRIPTION_NAME_LENGTH = 32;

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    @Test
    public void shouldOpenSubscription()
    {
        // when
        final ExecuteCommandResponse subscriptionResponse = apiRule
                .openTopicSubscription(0, "foo", 0)
                .await();

        // then
        assertThat(subscriptionResponse.key()).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void shouldCloseSubscription()
    {
        // given
        final ExecuteCommandResponse addResponse = apiRule
                .openTopicSubscription(0, "foo", 0)
                .await();

        final long subscriberKey = addResponse.key();

        // when
        final ControlMessageResponse removeResponse = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicId", 0)
                .put("subscriberKey", subscriberKey)
                .done()
            .sendAndAwait();

        // then
        assertThat(removeResponse.getData()).containsExactly(entry("subscriberKey", subscriberKey), entry("topicId", 0));
    }

    @Test
    public void shouldNotPushEventsAfterClose() throws InterruptedException
    {
        // given
        final ExecuteCommandResponse addResponse = apiRule
                .openTopicSubscription(0, "foo", 0)
                .await();

        final long subscriberKey = addResponse.key();

        apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicId", 0)
                .put("subscriberKey", subscriberKey)
                .done()
            .sendAndAwait();

        apiRule.moveSubscribedEventsStreamToTail();

        // when creating a task
        apiRule.createCmdRequest()
            .topicId(0)
            .eventTypeTask()
            .command()
                .put("eventType", "CREATE")
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
        final ExecuteCommandResponse addResponse = apiRule
            .openTopicSubscription(0, "foo", 0)
            .await();

        final long subscriberKey = addResponse.key();

        // then
        final List<SubscribedEvent> taskEvents = apiRule.subscribedEvents()
            .filter((e) -> e.eventType() == EventType.TASK_EVENT)
            .limit(2)
            .collect(Collectors.toList());

        assertThat(taskEvents).hasSize(2);
        assertThat(taskEvents.get(0).subscriptionId()).isEqualTo(subscriberKey);
        assertThat(taskEvents.get(0).subscriptionType()).isEqualTo(SubscriptionType.TOPIC_SUBSCRIPTION);
        assertThat(taskEvents.get(0).position()).isEqualTo(taskKey);
        assertThat(taskEvents.get(0).topicId()).isEqualTo(0);
        assertThat(taskEvents.get(0).event()).contains(entry("eventType", "CREATE"));

        assertThat(taskEvents.get(1).subscriptionId()).isEqualTo(subscriberKey);
        assertThat(taskEvents.get(1).subscriptionType()).isEqualTo(SubscriptionType.TOPIC_SUBSCRIPTION);
        assertThat(taskEvents.get(1).position()).isGreaterThan(taskKey);
        assertThat(taskEvents.get(1).topicId()).isEqualTo(0);
        assertThat(taskEvents.get(1).event()).contains(entry("eventType", "CREATED"));
    }

    @Test
    public void shouldReturnStartPositionOnOpen()
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

        // when
        final ExecuteCommandResponse addResponse = apiRule
            .openTopicSubscription(0, "foo", 0)
            .await();

        final long startPosition = (long) addResponse.getEvent().get("startPosition");

        // then
        final Optional<Long> event = apiRule.subscribedEvents()
            .map((e) -> e.position())
            .findFirst();

        assertThat(startPosition).isEqualTo(event.get());
    }

    @Test
    public void shouldNotOpenSubscriptionForNonExistingTopic()
    {
        // when
        final ErrorResponse errorResponse = apiRule
                .openTopicSubscription(Integer.MAX_VALUE, "foo", 0)
                .awaitError();

        // then
        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.TOPIC_NOT_FOUND);
        assertThat(errorResponse.getErrorData()).isEqualTo("Cannot execute command. Topic with id '" +
                Integer.MAX_VALUE + "' not found");
        assertThat(errorResponse.getFailedRequest().get("event")).isEqualTo("SUBSCRIBE");
    }

    @Test
    public void shouldNotCloseSubscriptionForNonExistingTopic()
    {
        // when
        final ErrorResponse errorResponse = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicId", Integer.MAX_VALUE)
                .put("subscriberKey", 0L)
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
                .put("subscriberKey", Long.MAX_VALUE)
                .done()
            .send().awaitError();

        // then
        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorResponse.getErrorData()).isEqualTo("Cannot close topic subscription. Subscription with id " +
                Long.MAX_VALUE + " is not open");
        assertThat(errorResponse.getFailedRequest().get("subscriberKey")).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    public void shouldOpenSubscriptionWithMaximumNameLength()
    {
        // when
        final String subscriptionName = getStringOfLength(MAXIMUM_SUBSCRIPTION_NAME_LENGTH);
        final ExecuteCommandResponse response = apiRule
                .openTopicSubscription(0, subscriptionName, 0)
                .await();

        // then
        assertThat(response.key()).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void shouldNotOpenSubscriptionWithOverlongName()
    {
        // given
        final String subscriptionName = getStringOfLength(MAXIMUM_SUBSCRIPTION_NAME_LENGTH + 1);

        // when
        final ErrorResponse errorResponse = apiRule
                .openTopicSubscription(0, subscriptionName, 0)
                .awaitError();

        // then
        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorResponse.getErrorData()).isEqualTo("Cannot open topic subscription " + subscriptionName +
                ". Subscription name must be " + MAXIMUM_SUBSCRIPTION_NAME_LENGTH + " characters or shorter.");
    }

    @Test
    public void shouldOpenSubscriptionAndForceStartPosition()
    {
        // given
        final ExecuteCommandResponse subscriptionResponse = apiRule
                .openTopicSubscription(0, "foo", 0)
                .await();

        final long subscriberKey = subscriptionResponse.key();

        apiRule.createCmdRequest()
            .topicId(0)
            .eventTypeTask()
            .command()
                .put("eventType", "CREATE")
                .put("type", "foo")
                .put("retries", 1)
                .done()
            .sendAndAwait();

        // wait for two task events
        final List<Long> taskEvents = apiRule.subscribedEvents()
            .filter((e) -> e.eventType() == EventType.TASK_EVENT)
            .limit(2)
            .map((e) -> e.position())
            .collect(Collectors.toList());

        apiRule.createCmdRequest()
            .eventTypeSubscription()
            .topicId(0)
            .command()
                .put("name", "foo")
                .put("event", "ACKNOWLEDGE")
                .put("ackPosition", taskEvents.get(1))
                .done()
            .sendAndAwait();

        apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicId", 0)
                .put("subscriberKey", subscriberKey)
                .done()
            .sendAndAwait();

        apiRule.moveSubscribedEventsStreamToTail();

        // when
        apiRule.createCmdRequest()
            .topicId(0)
            .eventTypeSubscriber()
            .command()
                .put("startPosition", taskEvents.get(0))
                .put("name", "foo")
                .put("event", "SUBSCRIBE")
                .put("forceStart", true)
                .done()
            .sendAndAwait();

        // then
        final List<Long> taskEventsAfterReopening = apiRule.subscribedEvents()
            .filter((e) -> e.eventType() == EventType.TASK_EVENT)
            .limit(2)
            .map((e) -> e.position())
            .collect(Collectors.toList());

        assertThat(taskEventsAfterReopening).hasSize(2);
        assertThat(taskEventsAfterReopening).containsExactlyElementsOf(taskEvents);
    }

    @Test
    public void shouldPersistStartPositionOnOpen()
    {
        // given
        final ExecuteCommandResponse subscriptionResponse = apiRule
                .openTopicSubscription(0, "foo", 0)
                .await();
        final long subscriberKey = subscriptionResponse.key();

        apiRule.createCmdRequest()
            .topicId(0)
            .eventTypeTask()
            .command()
                .put("eventType", "CREATE")
                .put("type", "foo")
                .put("retries", 1)
                .done()
            .sendAndAwait();

        // wait for two task events, but send no ACK
        final List<Long> taskEvents = apiRule.subscribedEvents()
            .filter((e) -> e.eventType() == EventType.TASK_EVENT)
            .limit(2)
            .map((e) -> e.position())
            .collect(Collectors.toList());

        apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicId", 0)
                .put("subscriberKey", subscriberKey)
                .done()
            .sendAndAwait();
        apiRule.moveSubscribedEventsStreamToTail();

        // when
        apiRule
            .openTopicSubscription(0, "foo", taskEvents.get(1))
            .await();

        // then the subscription should restart at the last ACKED position, which is the original start position
        final List<Long> taskEventsAfterReopen = apiRule.subscribedEvents()
            .filter((e) -> e.eventType() == EventType.TASK_EVENT)
            .limit(2)
            .map((e) -> e.position())
            .collect(Collectors.toList());

        assertThat(taskEventsAfterReopen).containsExactlyElementsOf(taskEvents);
    }

    @Test
    public void shouldReturnErrorIfSubscriptionProcessorRemovalFails()
    {
        // given
        final ExecuteCommandResponse subscriptionResponse = apiRule
            .openTopicSubscription(0, "foo", 0)
            .await();

        final long subscriberKey = subscriptionResponse.key();

        // and the subscription service has abnormally closed
        final ServiceName<Object> subscriptionServiceName = ServiceName.newServiceName("log.log.default-task-queue-log.subscription.processor.foo", Object.class);
        brokerRule.removeService(subscriptionServiceName);

        // when
        final ErrorResponse errorResponse = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicId", 0)
                .put("subscriberKey", subscriberKey)
                .done()
            .send().awaitError();

        // then
        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorResponse.getErrorData()).contains("Cannot close topic subscription. Cannot remove service");
    }

    @Test
    public void shouldCloseSubscriptionOnTransportChannelClose()
    {
        // given
        ExecuteCommandResponse subscriptionResponse = apiRule
            .openTopicSubscription(0, "foo", 0)
            .await();

        final long firstSubscriberKey = subscriptionResponse.key();

        // when the transport channel is closed
        apiRule.closeChannel();

        // then the subscription has been closed and we can reopen it
        apiRule.openChannel();
        subscriptionResponse = TestUtil.doRepeatedly(() ->
        {
            // it is not guaranteed this succeeds the first time as the event of the closed channel is asynchronous by nature
            return apiRule
                .openTopicSubscription(0, "foo", 0)
                .await();
        })
            .until((r) -> r != null);

        final long secondSubscriberKey = subscriptionResponse.key();

        assertThat(secondSubscriberKey).isNotEqualTo(firstSubscriberKey);
    }

    protected String getStringOfLength(int numCharacters)
    {
        final char[] characters = new char[numCharacters];
        Arrays.fill(characters, 'a');
        return new String(characters);
    }

}
