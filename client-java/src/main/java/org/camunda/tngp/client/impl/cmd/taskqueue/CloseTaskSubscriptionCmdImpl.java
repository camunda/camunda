package org.camunda.tngp.client.impl.cmd.taskqueue;

import static org.camunda.tngp.util.EnsureUtil.ensureGreaterThanOrEqual;
import static org.camunda.tngp.util.EnsureUtil.ensureNotNullOrEmpty;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.AbstractControlMessageWithoutResponseCmd;
import org.camunda.tngp.client.impl.data.MsgPackConverter;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;

public class CloseTaskSubscriptionCmdImpl extends AbstractControlMessageWithoutResponseCmd<TaskSubscription>
{
    protected final TaskSubscription subscription = new TaskSubscription();
    protected final MsgPackConverter msgPackConverter = new MsgPackConverter();

    private long subscriptionId = -1L;
    private final String topicName;
    private final int partitionId;

    public CloseTaskSubscriptionCmdImpl(final ClientCmdExecutor cmdExecutor, final ObjectMapper objectMapper, final String topicName, final int partitionId)
    {
        super(cmdExecutor, objectMapper, TaskSubscription.class, ControlMessageType.REMOVE_TASK_SUBSCRIPTION);
        this.topicName = topicName;
        this.partitionId = partitionId;
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
        ensureNotNullOrEmpty("topic name", topicName);
        ensureGreaterThanOrEqual("partition id", partitionId, 0);
    }

    @Override
    public void reset()
    {
        subscriptionId = -1L;
    }

    @Override
    protected Object writeCommand()
    {
        subscription.setSubscriberKey(subscriptionId);
        subscription.setTopicName(topicName);
        subscription.setPartitionId(partitionId);

        return subscription;
    }

}
