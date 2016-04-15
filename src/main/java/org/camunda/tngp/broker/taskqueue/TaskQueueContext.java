package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.broker.transport.worker.spi.ResourceContext;
import org.camunda.tngp.hashindex.HashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class TaskQueueContext implements ResourceContext
{
    protected final String taskQueueName;

    protected final int taskQueueId;

    protected Log log;

    protected HashIndex lockedTaskInstanceIndex;

    protected HashIndex taskTypePositionIndex;

    protected IdGenerator taskInstanceIdGenerator;

    public TaskQueueContext(String taskQueueName, int taskQueueId)
    {
        this.taskQueueName = taskQueueName;
        this.taskQueueId = taskQueueId;
    }

    public String getTaskQueueName()
    {
        return taskQueueName;
    }

    public int getTaskQueueId()
    {
        return taskQueueId;
    }

    public Log getLog()
    {
        return log;
    }

    public void setLog(Log log)
    {
        this.log = log;
    }

    public HashIndex getLockedTaskInstanceIndex()
    {
        return lockedTaskInstanceIndex;
    }

    public HashIndex getTaskTypePositionIndex()
    {
        return taskTypePositionIndex;
    }

    public void setLockedTaskInstanceIndex(HashIndex lockedTaskInstanceIndex)
    {
        this.lockedTaskInstanceIndex = lockedTaskInstanceIndex;
    }

    public void setTaskTypePositionIndex(HashIndex taskTypePositionIndex)
    {
        this.taskTypePositionIndex = taskTypePositionIndex;
    }

    public IdGenerator getTaskInstanceIdGenerator()
    {
        return taskInstanceIdGenerator;
    }

    public void setTaskInstanceIdGenerator(IdGenerator taskInstanceIdGenerator)
    {
        this.taskInstanceIdGenerator = taskInstanceIdGenerator;
    }

    @Override
    public int getResourceId()
    {
        return taskQueueId;
    }

    @Override
    public String getResourceName()
    {
        return taskQueueName;
    }

}
