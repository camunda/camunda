package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.transport.requestresponse.server.AsyncRequestWorkerContext;

public class TaskQueueWorkerContext extends AsyncRequestWorkerContext
{

    protected TaskQueueManager taskQueueManager;

    public TaskQueueManager getTaskQueueManager()
    {
        return taskQueueManager;
    }

    public void setTaskQueueManager(TaskQueueManager taskQueueManager)
    {
        this.taskQueueManager = taskQueueManager;
    }
}
