package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContext;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class TaskQueueContext implements ResourceContext
{
    protected final String taskQueueName;

    protected final int taskQueueId;

    protected Log log;

    protected HashIndexManager<Long2LongHashIndex> lockedTaskInstanceIndex;

    protected HashIndexManager<Bytes2LongHashIndex> taskTypePositionIndex;

    protected IdGenerator taskInstanceIdGenerator;

    protected TaskQueueIndexWriter taskQueueIndexWriter;

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

    public HashIndexManager<Long2LongHashIndex> getLockedTaskInstanceIndex()
    {
        return lockedTaskInstanceIndex;
    }

    public HashIndexManager<Bytes2LongHashIndex> getTaskTypePositionIndex()
    {
        return taskTypePositionIndex;
    }

    public void setLockedTaskInstanceIndex(HashIndexManager<Long2LongHashIndex> lockedTaskInstanceIndex)
    {
        this.lockedTaskInstanceIndex = lockedTaskInstanceIndex;
    }

    public void setTaskTypePositionIndex(HashIndexManager<Bytes2LongHashIndex> taskTypePositionIndex)
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

    public TaskQueueIndexWriter getTaskQueueIndexWriter()
    {
        return taskQueueIndexWriter;
    }

    public void setTaskQueueIndexWriter(TaskQueueIndexWriter taskQueueIndexWriter)
    {
        this.taskQueueIndexWriter = taskQueueIndexWriter;
    }
}
