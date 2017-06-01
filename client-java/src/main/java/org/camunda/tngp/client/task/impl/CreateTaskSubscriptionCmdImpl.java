package org.camunda.tngp.client.task.impl;

import static org.camunda.tngp.util.EnsureUtil.ensureGreaterThan;
import static org.camunda.tngp.util.EnsureUtil.ensureGreaterThanOrEqual;
import static org.camunda.tngp.util.EnsureUtil.ensureNotNull;
import static org.camunda.tngp.util.EnsureUtil.ensureNotNullOrEmpty;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.AbstractControlMessageCmd;
import org.camunda.tngp.client.impl.cmd.TopicCommand;
import org.camunda.tngp.client.task.impl.subscription.EventSubscriptionCreationResult;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;

public class CreateTaskSubscriptionCmdImpl extends AbstractControlMessageCmd<TaskSubscription, EventSubscriptionCreationResult> implements TopicCommand
{
    protected final TaskSubscription subscription = new TaskSubscription();

    private final String topicName;
    private final int partitionId;
    private String taskType;
    private long lockDuration = -1L;
    private long lockOwner = -1L;
    private int initialCredits = -1;

    public CreateTaskSubscriptionCmdImpl(final ClientCmdExecutor cmdExecutor, final ObjectMapper objectMapper, final String topicName, final int partitionId)
    {
        super(cmdExecutor, objectMapper, TaskSubscription.class, ControlMessageType.ADD_TASK_SUBSCRIPTION);
        this.topicName = topicName;
        this.partitionId = partitionId;
    }

    public CreateTaskSubscriptionCmdImpl lockOwner(final int lockOwner)
    {
        this.lockOwner = lockOwner;
        return this;
    }

    public CreateTaskSubscriptionCmdImpl initialCredits(final int initialCredits)
    {
        this.initialCredits = initialCredits;
        return this;
    }

    public CreateTaskSubscriptionCmdImpl lockDuration(final long lockDuration)
    {
        this.lockDuration = lockDuration;
        return this;
    }

    public CreateTaskSubscriptionCmdImpl taskType(final String taskType)
    {
        this.taskType = taskType;
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
        ensureNotNullOrEmpty("topic name", topicName);
        ensureGreaterThanOrEqual("partition id", partitionId, 0);
        ensureNotNull("task type", taskType);
        ensureGreaterThan("lock duration", lockDuration, 0);
        ensureGreaterThanOrEqual("lock owner", lockOwner, 0);
        ensureGreaterThan("initial credits", initialCredits, 0);
    }

    @Override
    public void reset()
    {
        taskType = null;
        lockDuration = -1L;
        lockOwner = -1L;
        initialCredits = -1;
    }

    @Override
    protected Object writeCommand()
    {
        subscription.setTopicName(topicName);
        subscription.setPartitionId(partitionId);
        subscription.setTaskType(taskType);
        subscription.setLockDuration(lockDuration);
        subscription.setLockOwner(lockOwner);
        subscription.setCredits(initialCredits);

        return subscription;
    }

    @Override
    protected EventSubscriptionCreationResult getResponseValue(final int channelId, final TaskSubscription data)
    {
        return new EventSubscriptionCreationResult(data.getSubscriberKey(), channelId);
    }

}
