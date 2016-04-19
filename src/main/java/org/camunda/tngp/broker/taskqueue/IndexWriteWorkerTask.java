package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.transport.requestresponse.server.WorkerTask;

public class IndexWriteWorkerTask implements WorkerTask<TaskQueueWorkerContext>
{
    @Override
    public int execute(TaskQueueWorkerContext context)
    {
        final int maxEntries = context.getResponsePool().getCapacity();

        int workCount = 0;

        final TaskQueueContext[] taskQueueContexts = context.getTaskQueueManager().getTaskQueueContexts();

        for (TaskQueueContext taskQueueContext : taskQueueContexts)
        {
            workCount += taskQueueContext.getTaskQueueIndexWriter().update(maxEntries);
        }

        return workCount;
    }

}
