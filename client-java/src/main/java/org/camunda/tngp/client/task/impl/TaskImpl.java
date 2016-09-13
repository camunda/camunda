package org.camunda.tngp.client.task.impl;

import java.util.Date;

import org.camunda.tngp.client.AsyncTasksClient;
import org.camunda.tngp.client.task.Task;

public class TaskImpl implements Task
{
    protected AsyncTasksClient tasksClient;

    protected long id;
    protected Long workflowInstanceId;
    protected String type;
    protected Date lockExpirationTime;
    protected int taskQueueId;

    protected int state;
    protected static final int STATE_LOCKED = 0;
    protected static final int STATE_COMPLETED = 1;

    public TaskImpl(
            AsyncTasksClient tasksClient,
            long id,
            Long wfInstanceId,
            String type,
            Date lockExpirationTime,
            int taskQueueId)
    {
        this.tasksClient = tasksClient;
        this.id = id;
        this.workflowInstanceId = wfInstanceId;
        this.type = type;
        this.lockExpirationTime = lockExpirationTime;
        this.taskQueueId = taskQueueId;
        this.state = STATE_LOCKED;
    }

    @Override
    public void complete()
    {
        tasksClient.complete()
            .taskId(id)
            .taskQueueId(taskQueueId)
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
    public Date getLockExpirationTime()
    {
        return lockExpirationTime;
    }

}
