package org.camunda.tngp.taskqueue.worker;

import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.log.index.LogIndex;
import org.camunda.tngp.taskqueue.index.TaskInstanceIndexManager;
import org.camunda.tngp.transport.requestresponse.server.AsyncWorkerContext;

public class TaskQueueContext extends AsyncWorkerContext
{
    protected Log log;

    protected TaskInstanceIndexManager taskInstanceIndexMapper;

    protected IdGenerator taskInstanceIdGenerator;

    public Log getLog()
    {
        return log;
    }

    public void setLog(Log log)
    {
        this.log = log;
        setAsyncWorkBuffer(log.getWriteBuffer());
    }

    public void setTaskInstanceIndexManager(TaskInstanceIndexManager taskInstanceIndexMapper)
    {
        this.taskInstanceIndexMapper = taskInstanceIndexMapper;
    }

    public TaskInstanceIndexManager getTaskInstanceIndexMapper()
    {
        return taskInstanceIndexMapper;
    }

    public LogIndex getTaskInstanceIndex()
    {
        return taskInstanceIndexMapper.getLogIndex();
    }

    public IdGenerator getTaskInstanceIdGenerator()
    {
        return taskInstanceIdGenerator;
    }

    public void setTaskInstanceIdGenerator(IdGenerator taskInstanceIdGenerator)
    {
        this.taskInstanceIdGenerator = taskInstanceIdGenerator;
    }
}
