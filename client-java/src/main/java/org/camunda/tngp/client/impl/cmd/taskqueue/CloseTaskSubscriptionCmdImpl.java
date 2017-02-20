package org.camunda.tngp.client.impl.cmd.taskqueue;

import static org.camunda.tngp.util.EnsureUtil.ensureGreaterThanOrEqual;
import static org.camunda.tngp.util.EnsureUtil.ensureNotNull;

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
    private long topicId = -1L;
    private String taskType;

    public CloseTaskSubscriptionCmdImpl(ClientCmdExecutor cmdExecutor, final ObjectMapper objectMapper)
    {
        super(cmdExecutor, objectMapper, TaskSubscription.class, ControlMessageType.REMOVE_TASK_SUBSCRIPTION);
    }

    public CloseTaskSubscriptionCmdImpl subscriptionId(long subscriptionId)
    {
        this.subscriptionId = subscriptionId;
        return this;
    }

    public CloseTaskSubscriptionCmdImpl topicId(long topicId)
    {
        this.topicId = topicId;
        return this;
    }

    public CloseTaskSubscriptionCmdImpl taskType(String taskType)
    {
        this.taskType = taskType;
        return this;
    }

    @Override
    public void validate()
    {
        ensureGreaterThanOrEqual("subscription id", subscriptionId, 0);
        ensureGreaterThanOrEqual("topic id", topicId, 0);
        ensureNotNull("task type", taskType);
    }

    @Override
    public void reset()
    {
        subscriptionId = -1L;
        topicId = -1;
        taskType = null;
    }

    @Override
    protected Object writeCommand()
    {
        subscription.setId(subscriptionId);
        subscription.setTopicId(topicId);
        subscription.setTaskType(taskType);

        return subscription;
    }

}
