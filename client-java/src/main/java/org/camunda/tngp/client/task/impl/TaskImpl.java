package org.camunda.tngp.client.task.impl;

import java.time.Instant;

import org.agrona.DirectBuffer;
import org.camunda.tngp.client.AsyncTasksClient;
import org.camunda.tngp.client.task.Task;
import org.camunda.tngp.protocol.taskqueue.SubscribedTaskReader;

public class TaskImpl implements Task
{
    protected final AsyncTasksClient tasksClient;

    protected final long id;
    protected final Long workflowInstanceId;
    protected final String type;
    protected final Instant lockExpirationTime;
    protected final int taskQueueId;
    protected final PayloadField payload = new PayloadField();

    protected int state;
    protected static final int STATE_LOCKED = 0;
    protected static final int STATE_COMPLETED = 1;


    public TaskImpl(
            AsyncTasksClient tasksClient,
            SubscribedTaskReader taskReader,
            TaskSubscriptionImpl subscription)
    {
        this.tasksClient = tasksClient;
        this.id = taskReader.taskId();
        this.workflowInstanceId = taskReader.wfInstanceId();
        this.type = subscription.getTaskType();
        this.lockExpirationTime = Instant.ofEpochMilli(taskReader.lockTime());
        this.taskQueueId = subscription.getTaskQueueId();
        this.state = STATE_LOCKED;

        final DirectBuffer payloadBuffer = taskReader.payload();
        payload.initFromPayloadBuffer(payloadBuffer, 0, payloadBuffer.capacity());
    }

    @Override
    public void complete()
    {
        final DirectBuffer payloadBuffer = payload.getPayloadBuffer();

        tasksClient.complete()
            .taskId(id)
            .taskQueueId(taskQueueId)
            .payload(payloadBuffer, 0, payloadBuffer.capacity())
            .execute();

        state = STATE_COMPLETED;
    }

    public boolean isCompleted()
    {
        return state == STATE_COMPLETED;
    }

    @Override
    public long getId()
    {
        return id;
    }

    @Override
    public Long getWorkflowInstanceId()
    {
        return workflowInstanceId;
    }

    @Override
    public String getType()
    {
        return type;
    }

    @Override
    public Instant getLockExpirationTime()
    {
        return lockExpirationTime;
    }

    @Override
    public String getPayloadString()
    {
        return payload.getPayloadString();
    }

    @Override
    public void setPayloadString(String updatedPayload)
    {
        payload.setPayloadString(updatedPayload);
    }

}
