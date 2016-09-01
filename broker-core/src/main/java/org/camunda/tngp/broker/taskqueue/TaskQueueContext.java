package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.broker.log.LogConsumer;
import org.camunda.tngp.broker.log.LogWriter;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContext;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class TaskQueueContext implements ResourceContext
{
    protected final String taskQueueName;

    protected final int taskQueueId;

    protected Log log;

    protected LogWriter logWriter;

    protected HashIndexManager<Bytes2LongHashIndex> taskTypePositionIndex;

    protected IdGenerator taskInstanceIdGenerator;

    protected LogConsumer logConsumer;

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

    public LogWriter getLogWriter()
    {
        return logWriter;
    }

    public void setLogWriter(LogWriter logWriter)
    {
        this.logWriter = logWriter;
    }

    public HashIndexManager<Bytes2LongHashIndex> getTaskTypePositionIndex()
    {
        return taskTypePositionIndex;
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

    public LogConsumer getLogConsumer()
    {
        return logConsumer;
    }

    public void setLogConsumer(LogConsumer logConsumer)
    {
        this.logConsumer = logConsumer;
    }
}
