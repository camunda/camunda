package org.camunda.tngp.taskqueue.worker;

import org.camunda.tngp.log.index.LogIndex;
import org.camunda.tngp.transport.requestresponse.server.AsyncWorker;

public class TaskQueueWorker extends AsyncWorker
{
    protected LogIndex taskInstanceIndex;

    public TaskQueueWorker(String name, TaskQueueContext context)
    {
        super(name, context);

        this.taskInstanceIndex = context.getTaskInstanceIndex();
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = super.doWork();

        taskInstanceIndex.updateIndex();

        return workCount;
    }
}
