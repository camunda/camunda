package org.camunda.tngp.client.task.impl;

import static org.camunda.tngp.util.EnsureUtil.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.AbstractControlMessageWithoutResponseCmd;
import org.camunda.tngp.client.impl.cmd.TopicCommand;
import org.camunda.tngp.client.impl.data.MsgPackConverter;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;

public class IncreaseTaskSubscriptionCreditsCmdImpl extends AbstractControlMessageWithoutResponseCmd<TaskSubscription> implements TopicCommand
{
    protected final TaskSubscription subscription = new TaskSubscription();
    protected final MsgPackConverter msgPackConverter = new MsgPackConverter();

    private final String topicName;
    private final int partitionId;
    private long subscriptionId = -1L;
    private int credits = 0;

    public IncreaseTaskSubscriptionCreditsCmdImpl(final ClientCmdExecutor cmdExecutor, final ObjectMapper objectMapper, final String topicName, final int partitionId)
    {
        super(cmdExecutor, objectMapper, TaskSubscription.class, ControlMessageType.INCREASE_TASK_SUBSCRIPTION_CREDITS);
        this.topicName = topicName;
        this.partitionId = partitionId;
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
    public String getTopicName()
    {
        return topicName;
    }

    @Override
    public int getPartitionId()
    {
        return partitionId;
    }

    @Override
    public void validate()
    {
        ensureGreaterThanOrEqual("subscription id", subscriptionId, 0);
        ensureNotNullOrEmpty("topic name", topicName);
        ensureGreaterThanOrEqual("partition id", partitionId, 0);
        ensureGreaterThan("credits", credits, 0);
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
        subscription.setTopicName(topicName);
        subscription.setPartitionId(partitionId);
        subscription.setCredits(credits);

        return subscription;
    }

}
