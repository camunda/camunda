package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.transport.requestresponse.server.WorkerTask;

public class TaskQueueIndexWriteWorkerTask implements WorkerTask<TaskQueueWorkerContext>
{
    @Override
    public int execute(TaskQueueWorkerContext context)
    {
        final int maxEntries = context.getResponsePool().getCapacity();

        int workCount = 0;

        final TaskQueueContext[] taskQueueContexts = context.getTaskQueueManager().getContexts();

        for (int i = 0; i < taskQueueContexts.length; i++)
        {
            workCount += taskQueueContexts[i].getTaskQueueIndexWriter().update(maxEntries);
        }

        return workCount;
    }

}
