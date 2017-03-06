package org.camunda.tngp.client.impl.cmd.taskqueue;

import static org.camunda.tngp.util.EnsureUtil.ensureGreaterThanOrEqual;

import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.AbstractControlMessageWithoutResponseCmd;
import org.camunda.tngp.client.impl.data.MsgPackConverter;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CloseTaskSubscriptionCmdImpl extends AbstractControlMessageWithoutResponseCmd<TaskSubscription>
{
    protected final TaskSubscription subscription = new TaskSubscription();
    protected final MsgPackConverter msgPackConverter = new MsgPackConverter();

    private long subscriptionId = -1L;
    private final int topicId;

    public CloseTaskSubscriptionCmdImpl(ClientCmdExecutor cmdExecutor, final ObjectMapper objectMapper, int topicId)
    {
        super(cmdExecutor, objectMapper, TaskSubscription.class, ControlMessageType.REMOVE_TASK_SUBSCRIPTION);
        this.topicId = topicId;
    }

    public CloseTaskSubscriptionCmdImpl subscriptionId(long subscriptionId)
    {
        this.subscriptionId = subscriptionId;
        return this;
    }

    @Override
    public void validate()
    {
        ensureGreaterThanOrEqual("subscription id", subscriptionId, 0);
        ensureGreaterThanOrEqual("topic id", topicId, 0);
    }

    @Override
    public void reset()
    {
        subscriptionId = -1L;
    }

    @Override
    protected Object writeCommand()
    {
        subscription.setId(subscriptionId);
        subscription.setTopicId(topicId);

        return subscription;
    }

}
