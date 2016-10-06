package org.camunda.tngp.broker.taskqueue.subscription;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.taskqueue.request.handler.TaskTypeHash;
import org.camunda.tngp.protocol.wf.Constants;

public class TaskSubscription
{
    protected long id;
    protected int channelId;
    protected boolean isAdhoc;

    protected int consumerId;
    protected long lockDuration;
    protected long credits;

    protected UnsafeBuffer taskTypeBuffer = new UnsafeBuffer(new byte[Constants.TASK_TYPE_MAX_LENGTH]);
    protected UnsafeBuffer taskTypeView = new UnsafeBuffer(0, 0);
    protected int taskTypeHash;

    /**
     * Creates an adhoc subscription
     */
    public TaskSubscription(long id)
    {
        this(id, -1);
        this.isAdhoc = true;
    }

    /**
     * Creates a long running subscription
     */
    public TaskSubscription(long id, int channelId)
    {
        this.id = id;
        this.channelId = channelId;
        this.isAdhoc = false;
    }

    public long getId()
    {
        return id;
    }

    public int getChannelId()
    {
        return channelId;
    }

    public int getConsumerId()
    {
        return consumerId;
    }

    public void setConsumerId(int consumerId)
    {
        this.consumerId = consumerId;
    }

    public long getLockDuration()
    {
        return lockDuration;
    }

    public void setLockDuration(long lockDuration)
    {
        this.lockDuration = lockDuration;
    }

    public long getCredits()
    {
        return credits;
    }

    public void setCredits(long credits)
    {
        this.credits = credits;
    }

    public DirectBuffer getTaskType()
    {
        return taskTypeView;
    }

    public void setTaskType(DirectBuffer buffer, int offset, int length)
    {
        // actual copy is required since the subscription lives longer than the request buffer
        buffer.getBytes(0, taskTypeBuffer, 0, buffer.capacity());
        taskTypeView.wrap(buffer, 0, buffer.capacity());

        this.taskTypeHash = TaskTypeHash.hashCode(buffer, 0, buffer.capacity());
    }

    public int getTaskTypeHash()
    {
        return taskTypeHash;
    }

    public boolean isAdhoc()
    {
        return isAdhoc;
    }


}
