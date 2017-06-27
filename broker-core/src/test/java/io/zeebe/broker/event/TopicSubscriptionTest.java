package io.zeebe.broker.event;

import static org.assertj.core.api.Assertions.*;
import static io.zeebe.logstreams.log.LogStream.*;
import static io.zeebe.test.util.BufferAssert.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ControlMessageResponse;
import io.zeebe.test.broker.protocol.clientapi.ErrorResponse;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.RawMessage;
import io.zeebe.test.broker.protocol.clientapi.SubscribedEvent;
import io.zeebe.test.util.TestUtil;
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
                .openTopicSubscription("foo", 0)
                .await();

        // then
        assertThat(subscriptionResponse.key()).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void shouldCloseSubscription()
    {
        // given
        final ExecuteCommandResponse addResponse = apiRule
                .openTopicSubscription("foo", 0)
                .await();

        final long subscriberKey = addResponse.key();

        // when
        final ControlMessageResponse removeResponse = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicName", DEFAULT_TOPIC_NAME)
                .put("partitionId", DEFAULT_PARTITION_ID)
                .put("subscriberKey", subscriberKey)
                .done()
            .sendAndAwait();

        // then
        assertThat(removeResponse.getData()).containsOnly(
            entry("subscriberKey", subscriberKey),
            entry("topicName", DEFAULT_TOPIC_NAME),
            entry("partitionId", DEFAULT_PARTITION_ID)
        );
    }

    @Test
    public void shouldNotPushEventsAfterClose() throws InterruptedException
    {
        // given
        final ExecuteCommandResponse addResponse = apiRule
                .openTopicSubscription("foo", 0)
                .await();

        final long subscriberKey = addResponse.key();

        apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicName", DEFAULT_TOPIC_NAME)
                .put("partitionId", DEFAULT_PARTITION_ID)
                .put("subscriberKey", subscriberKey)
                .done()
            .sendAndAwait();

        apiRule.moveMessageStreamToTail();

        // when creating a task
        apiRule.createCmdRequest()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
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
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
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
            .openTopicSubscription("foo", 0)
            .await();

        final long subscriberKey = addResponse.key();

        // then
        final List<SubscribedEvent> taskEvents = apiRule.subscribedEvents()
            .filter((e) -> e.eventType() == EventType.TASK_EVENT)
            .limit(2)
            .collect(Collectors.toList());

        assertThat(taskEvents).hasSize(2);
        SubscribedEvent taskEvent = taskEvents.get(0);
        assertThat(taskEvent.subscriberKey()).isEqualTo(subscriberKey);
        assertThat(taskEvent.subscriptionType()).isEqualTo(SubscriptionType.TOPIC_SUBSCRIPTION);
        assertThat(taskEvent.position()).isEqualTo(taskKey);
        assertThat(taskEvent.topicName()).isEqualTo(DEFAULT_TOPIC_NAME);
        assertThat(taskEvent.partitionId()).isEqualTo(DEFAULT_PARTITION_ID);
        assertThat(taskEvent.event()).contains(entry("eventType", "CREATE"));

        taskEvent = taskEvents.get(1);
        assertThat(taskEvent.subscriberKey()).isEqualTo(subscriberKey);
        assertThat(taskEvent.subscriptionType()).isEqualTo(SubscriptionType.TOPIC_SUBSCRIPTION);
        assertThat(taskEvent.position()).isGreaterThan(taskKey);
        assertThat(taskEvent.topicName()).isEqualTo(DEFAULT_TOPIC_NAME);
        assertThat(taskEvent.partitionId()).isEqualTo(DEFAULT_PARTITION_ID);
        assertThat(taskEvent.event()).contains(entry("eventType", "CREATED"));
    }

    @Test
    public void shouldReturnStartPositionOnOpen()
    {
        // given
        apiRule.createCmdRequest()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
            .eventTypeTask()
            .command()
                .put("eventType", "CREATE")
                .put("type", "foo")
                .put("retries", 1)
                .done()
            .sendAndAwait();

        // when
        final ExecuteCommandResponse addResponse = apiRule
            .openTopicSubscription("foo", 0)
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
        // given
        final ExecuteCommandRequest request = apiRule.openTopicSubscription("unknown-topic", DEFAULT_PARTITION_ID, "foo", 0);

        // when
        final ErrorResponse errorResponse = request.awaitError();

        // then
        final String expectedMessage = String.format(
            "Cannot execute command. Topic with name '%s' and partition id '%d' not found", "unknown-topic", DEFAULT_PARTITION_ID);

        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.TOPIC_NOT_FOUND);
        assertThat(errorResponse.getErrorData()).isEqualTo(expectedMessage);
        assertThatBuffer(errorResponse.getFailedRequestBuffer()).hasBytes(request);
    }

    @Test
    public void shouldNotOpenSubscriptionForNonExistingPartition()
    {
        // given
        final ExecuteCommandRequest request = apiRule.openTopicSubscription(DEFAULT_TOPIC_NAME, 999, "foo", 0);

        // when
        final ErrorResponse errorResponse = request.awaitError();

        // then
        final String expectedMessage = String.format(
            "Cannot execute command. Topic with name '%s' and partition id '%d' not found", DEFAULT_TOPIC_NAME, 999);

        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.TOPIC_NOT_FOUND);
        assertThat(errorResponse.getErrorData()).isEqualTo(expectedMessage);
        assertThatBuffer(errorResponse.getFailedRequestBuffer()).hasBytes(request);
    }

    @Test
    public void shouldNotCloseSubscriptionForNonExistingTopic()
    {
        // when
        final ErrorResponse errorResponse = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicName", "unknown-topic")
                .put("partitionId", DEFAULT_PARTITION_ID)
                .put("subscriberKey", 0L)
                .done()
            .send().awaitError();

        // then
        final String expectedMessage = String.format("Cannot close topic subscription. No subscription management " +
            "processor registered for topic '%s' and partition '%d'", "unknown-topic", DEFAULT_PARTITION_ID);

        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorResponse.getErrorData()).isEqualTo(expectedMessage);

        assertThat(errorResponse.getFailedRequest())
            .containsEntry("topicName", "unknown-topic")
            .containsEntry("partitionId", DEFAULT_PARTITION_ID);
    }

    @Test
    public void shouldNotCloseSubscriptionForNonExistingPartition()
    {
        // when
        final ErrorResponse errorResponse = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicName", DEFAULT_TOPIC_NAME)
                .put("partitionId", 999)
                .put("subscriberKey", 0L)
                .done()
            .send().awaitError();

        // then
        final String expectedMessage = String.format("Cannot close topic subscription. No subscription management " +
            "processor registered for topic '%s' and partition '%d'", DEFAULT_TOPIC_NAME, 999);

        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorResponse.getErrorData()).isEqualTo(expectedMessage);

        assertThat(errorResponse.getFailedRequest())
            .containsEntry("topicName", DEFAULT_TOPIC_NAME)
            .containsEntry("partitionId", 999);
    }


    @Test
    public void shouldCloseSubscriptionNonExistingSubscription()
    {
        // when
        final ControlMessageResponse removeResponse = apiRule.createControlMessageRequest()
                .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
                .data()
                    .put("topicName", DEFAULT_TOPIC_NAME)
                    .put("partitionId", DEFAULT_PARTITION_ID)
                    .put("subscriberKey", Long.MAX_VALUE)
                    .done()
                .sendAndAwait();

        // then
        assertThat(removeResponse.getData())
            .containsOnly(
                entry("subscriberKey", Long.MAX_VALUE),
                entry("topicName", DEFAULT_TOPIC_NAME),
                entry("partitionId", DEFAULT_PARTITION_ID)
            );
    }

    @Test
    public void shouldOpenSubscriptionWithMaximumNameLength()
    {
        // when
        final String subscriptionName = getStringOfLength(MAXIMUM_SUBSCRIPTION_NAME_LENGTH);
        final ExecuteCommandResponse response = apiRule
                .openTopicSubscription(subscriptionName, 0)
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
                .openTopicSubscription(subscriptionName, 0)
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
                .openTopicSubscription("foo", 0)
                .await();

        final long subscriberKey = subscriptionResponse.key();

        apiRule.createCmdRequest()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
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
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
            .command()
                .put("name", "foo")
                .put("eventType", "ACKNOWLEDGE")
                .put("ackPosition", taskEvents.get(1))
                .done()
            .sendAndAwait();

        apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicName", DEFAULT_TOPIC_NAME)
                .put("partitionId", DEFAULT_PARTITION_ID)
                .put("subscriberKey", subscriberKey)
                .done()
            .sendAndAwait();

        apiRule.moveMessageStreamToTail();

        // when
        apiRule.createCmdRequest()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
            .eventTypeSubscriber()
            .command()
                .put("startPosition", taskEvents.get(0))
                .put("name", "foo")
                .put("eventType", "SUBSCRIBE")
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
                .openTopicSubscription("foo", 0)
                .await();
        final long subscriberKey = subscriptionResponse.key();

        apiRule.createCmdRequest()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
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
                .put("topicName", DEFAULT_TOPIC_NAME)
                .put("partitionId", DEFAULT_PARTITION_ID)
                .put("subscriberKey", subscriberKey)
                .done()
            .sendAndAwait();
        apiRule.moveMessageStreamToTail();

        // when
        apiRule
            .openTopicSubscription("foo", taskEvents.get(1))
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
            .openTopicSubscription("foo", 0)
            .await();

        final long subscriberKey = subscriptionResponse.key();

        // and the subscription service has abnormally closed
        final String name = "log.log." + DEFAULT_LOG_NAME +  ".subscription.push.foo";
        final ServiceName<Object> subscriptionServiceName = ServiceName.newServiceName(name, Object.class);
        brokerRule.removeService(subscriptionServiceName);

        // when
        final ErrorResponse errorResponse = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicName", DEFAULT_TOPIC_NAME)
                .put("partitionId", DEFAULT_PARTITION_ID)
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
            .openTopicSubscription("foo", 0)
            .await();

        final long firstSubscriberKey = subscriptionResponse.key();

        // when the transport channel is closed
        apiRule.interruptAllChannels();

        // then the subscription has been closed and we can reopen it
        subscriptionResponse = TestUtil.doRepeatedly(() ->
        {
            // it is not guaranteed this succeeds the first time as the event of the closed channel is asynchronous by nature
            return apiRule
                .openTopicSubscription("foo", 0)
                .await();
        })
            .until((r) -> r != null);

        final long secondSubscriberKey = subscriptionResponse.key();

        assertThat(secondSubscriberKey).isNotEqualTo(firstSubscriberKey);
    }

    @Test
    public void shouldNotPushEventsBeforeSubscriptionResponse()
    {
        // given
        apiRule.createCmdRequest()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
            .eventTypeTask()
            .command()
                .put("eventType", "CREATE")
                .put("type", "foo")
                .put("retries", 1)
                .done()
            .sendAndAwait();

        // when
        apiRule
            .openTopicSubscription("foo", 0)
            .await();

        // then
        final RawMessage subscriptionResponse = apiRule.commandResponses()
            .filter((m) -> "SUBSCRIBED".equals(asCommandResponse(m).getEvent().get("eventType")))
            .findFirst()
            .get();

        apiRule.moveMessageStreamToHead();

        final RawMessage firstPushedEvent = apiRule.subscribedEvents()
            .findFirst()
            .get()
            .getRawMessage();

        assertThat(firstPushedEvent.getSequenceNumber()).isGreaterThan(subscriptionResponse.getSequenceNumber());
    }

    protected String getStringOfLength(int numCharacters)
    {
        final char[] characters = new char[numCharacters];
        Arrays.fill(characters, 'a');
        return new String(characters);
    }

    protected static ExecuteCommandResponse asCommandResponse(RawMessage message)
    {
        final ExecuteCommandResponse response = new ExecuteCommandResponse(new MsgPackHelper());
        response.wrap(message.getMessage(), 0, message.getMessage().capacity());
        return response;
    }



}
