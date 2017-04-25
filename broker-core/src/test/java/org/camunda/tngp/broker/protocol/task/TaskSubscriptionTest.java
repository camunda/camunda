package org.camunda.tngp.broker.protocol.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.camunda.tngp.broker.protocol.clientapi.EmbeddedBrokerRule;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.protocol.clientapi.ErrorCode;
import org.camunda.tngp.protocol.clientapi.SubscriptionType;
import org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule;
import org.camunda.tngp.test.broker.protocol.clientapi.ControlMessageResponse;
import org.camunda.tngp.test.broker.protocol.clientapi.ErrorResponse;
import org.camunda.tngp.test.broker.protocol.clientapi.ExecuteCommandResponse;
import org.camunda.tngp.test.broker.protocol.clientapi.SubscribedEvent;
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
    public void shouldCloseSubscriptionOnTransportChannelClose() throws InterruptedException
    {
        // given
        apiRule
            .openTaskSubscription(0, "foo")
            .await();

        // when the transport channel is closed
        apiRule.closeChannel();

        // then the subscription has been closed, so we can create a new task and lock it for a new subscription
        apiRule.openChannel();
        Thread.sleep(1000L); // closing subscriptions happens asynchronously

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

        final ControlMessageResponse subscriptionResponse = apiRule
            .openTaskSubscription(0, "foo")
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
                .put("topicId", 0)
                .done()
            .send().awaitError();

        // then
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(error.getErrorData()).isEqualTo("Cannot increase task subscription credits. Credits must be positive.");
    }

}
