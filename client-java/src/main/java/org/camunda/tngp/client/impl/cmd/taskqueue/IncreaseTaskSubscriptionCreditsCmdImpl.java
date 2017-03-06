package org.camunda.tngp.client.impl.cmd.taskqueue;

import static org.camunda.tngp.util.EnsureUtil.ensureGreaterThan;
import static org.camunda.tngp.util.EnsureUtil.ensureGreaterThanOrEqual;

import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.AbstractControlMessageWithoutResponseCmd;
import org.camunda.tngp.client.impl.data.MsgPackConverter;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;

import com.fasterxml.jackson.databind.ObjectMapper;

public class IncreaseTaskSubscriptionCreditsCmdImpl extends AbstractControlMessageWithoutResponseCmd<TaskSubscription>
{
    protected final TaskSubscription subscription = new TaskSubscription();
    protected final MsgPackConverter msgPackConverter = new MsgPackConverter();

    private final int topicId;
    private long subscriptionId = -1L;
    private int credits = 0;

    public IncreaseTaskSubscriptionCreditsCmdImpl(ClientCmdExecutor cmdExecutor, final ObjectMapper objectMapper, int topicId)
    {
        super(cmdExecutor, objectMapper, TaskSubscription.class, ControlMessageType.INCREASE_TASK_SUBSCRIPTION_CREDITS);
        this.topicId = topicId;
    }

    public IncreaseTaskSubscriptionCreditsCmdImpl subscriptionId(long subscriptionId)
    {
        this.subscriptionId = subscriptionId;
        return this;
    }

    public IncreaseTaskSubscriptionCreditsCmdImpl credits(int credits)
    {
        this.credits = credits;
        return this;
    }

    @Override
    public void validate()
    {
        ensureGreaterThanOrEqual("subscription id", subscriptionId, 0);
        ensureGreaterThanOrEqual("topic id", topicId, 0);
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
        subscription.setId(subscriptionId);
        subscription.setTopicId(topicId);
        subscription.setCredits(credits);

        return subscription;
    }

}
