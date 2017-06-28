package org.camunda.tngp.client.task.impl;

import static org.camunda.tngp.util.EnsureUtil.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.tngp.client.impl.ClientCommandManager;
import org.camunda.tngp.client.impl.Topic;
import org.camunda.tngp.client.impl.cmd.AbstractControlMessageWithoutResponseCmd;
import org.camunda.tngp.client.impl.data.MsgPackConverter;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;

public class IncreaseTaskSubscriptionCreditsCmdImpl extends AbstractControlMessageWithoutResponseCmd<TaskSubscription>
{
    protected final TaskSubscription subscription = new TaskSubscription();
    protected final MsgPackConverter msgPackConverter = new MsgPackConverter();

    private long subscriptionId = -1L;
    private int credits = 0;

    public IncreaseTaskSubscriptionCreditsCmdImpl(final ClientCommandManager commandManager, final ObjectMapper objectMapper, final Topic topic)
    {
        super(commandManager, objectMapper, topic, TaskSubscription.class, ControlMessageType.INCREASE_TASK_SUBSCRIPTION_CREDITS);
    }

    public IncreaseTaskSubscriptionCreditsCmdImpl subscriptionId(final long subscriptionId)
    {
        this.subscriptionId = subscriptionId;
        return this;
    }

    public IncreaseTaskSubscriptionCreditsCmdImpl credits(final int credits)
    {
        this.credits = credits;
        return this;
    }

    @Override
    public void validate()
    {
        ensureGreaterThanOrEqual("subscription id", subscriptionId, 0);
        ensureGreaterThan("credits", credits, 0);
        topic.validate();
    }

    @Override
    public void reset()
    {
        subscriptionId = -1L;
        credits = 0;
    }

    @Override
    protected Object writeCommand()
    {
        subscription.setSubscriberKey(subscriptionId);
        subscription.setTopicName(topic.getTopicName());
        subscription.setPartitionId(topic.getPartitionId());
        subscription.setCredits(credits);

        return subscription;
    }

}
