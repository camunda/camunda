package io.zeebe.broker.task;

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.logstreams.log.LogStream.DEFAULT_PARTITION_ID;
import static io.zeebe.logstreams.log.LogStream.DEFAULT_TOPIC_NAME;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.taskEvents;

import java.util.Optional;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.test.broker.protocol.clientapi.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;


public class TaskSubscriptionTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    @Test
    public void shouldAddTaskSubscription() throws InterruptedException
    {
        // given
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TASK_SUBSCRIPTION)
            .data()
                .put("topicName", DEFAULT_TOPIC_NAME)
                .put("partitionId", DEFAULT_PARTITION_ID)
                .put("taskType", "foo")
                .put("lockDuration", 1000L)
                .put("lockOwner", "bar")
                .put("credits", 5)
                .done()
            .send();

        // when
        final ExecuteCommandResponse createTaskResponse = apiRule.createCmdRequest()
                .topicName(DEFAULT_TOPIC_NAME)
                .partitionId(DEFAULT_PARTITION_ID)
                .eventTypeTask()
                .command()
                    .put("eventType", "CREATE")
                    .put("type", "foo")
                    .put("retries", 3)
                .done()
                .sendAndAwait();

        // then
        final long taskKey = createTaskResponse.key();

        final SubscribedEvent taskEvent = apiRule.topic().receiveSingleEvent(taskEvents("LOCKED"));
        assertThat(taskEvent.key()).isEqualTo(taskKey);
        assertThat(taskEvent.event())
            .containsEntry("type", "foo")
            .containsEntry("retries", 3)
            .containsEntry("lockOwner", "bar");
    }

    @Test
    public void shouldCloseSubscriptionOnTransportChannelClose() throws InterruptedException
    {
        // given
        apiRule
            .openTaskSubscription("foo")
            .await();

        // when the transport channel is closed
        apiRule.interruptAllChannels();

        // then the subscription has been closed, so we can create a new task and lock it for a new subscription
        Thread.sleep(1000L); // closing subscriptions happens asynchronously

        final ExecuteCommandResponse createTaskResponse = apiRule.createCmdRequest()
                .topicName(DEFAULT_TOPIC_NAME)
                .partitionId(0)
                .eventTypeTask()
                .command()
                .put("eventType", "CREATE")
                .put("type", "foo")
                .put("retries", 1)
                .done()
                .sendAndAwait();

        final long taskKey = createTaskResponse.key();

        final ControlMessageResponse subscriptionResponse = apiRule
            .openTaskSubscription("foo")
            .await();
        final int secondSubscriberKey = (int) subscriptionResponse.getData().get("subscriberKey");

        final Optional<SubscribedEvent> taskEvent = apiRule.subscribedEvents()
            .filter((s) -> s.subscriptionType() == SubscriptionType.TASK_SUBSCRIPTION
                && s.key() == taskKey)
            .findFirst();

        assertThat(taskEvent).isPresent();
        assertThat(taskEvent.get().subscriberKey()).isEqualTo(secondSubscriberKey);
    }

    @Test
    public void shouldRejectCreditsEqualToZero()
    {
        // when
        final ErrorResponse error = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.INCREASE_TASK_SUBSCRIPTION_CREDITS)
            .data()
                .put("subscriberKey", 1)
                .put("credits", 0)
                .put("topicName", DEFAULT_TOPIC_NAME)
                .put("partitionId", DEFAULT_PARTITION_ID)
                .done()
            .send().awaitError();

        // then
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(error.getErrorData()).isEqualTo("Cannot increase task subscription credits. Credits must be positive.");
    }


    @Test
    public void shouldRejectNegativeCredits()
    {
        // when
        final ErrorResponse error = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.INCREASE_TASK_SUBSCRIPTION_CREDITS)
            .data()
                .put("subscriberKey", 1)
                .put("credits", -10)
                .put("topicName", DEFAULT_TOPIC_NAME)
                .put("partitionId", DEFAULT_PARTITION_ID)
                .done()
            .send().awaitError();

        // then
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(error.getErrorData()).isEqualTo("Cannot increase task subscription credits. Credits must be positive.");
    }

}
