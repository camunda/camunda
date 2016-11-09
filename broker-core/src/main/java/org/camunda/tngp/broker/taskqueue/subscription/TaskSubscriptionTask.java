package org.camunda.tngp.broker.taskqueue.subscription;

import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.taskqueue.TaskQueueWorkerContext;
import org.camunda.tngp.transport.requestresponse.server.WorkerTask;

public class TaskSubscriptionTask implements WorkerTask<TaskQueueWorkerContext>
{

    @Override
    public int execute(TaskQueueWorkerContext context)
    {
        final TaskQueueContext[] taskQueueContexts = context.getTaskQueueManager().getContexts();

        int workCount = 0;

        for (int i = 0; i < taskQueueContexts.length; i++)
        {
            final TaskQueueContext taskQueueContext = taskQueueContexts[i];

            if (!taskQueueContext.getLockedTasksOperator().hasPendingTasks())
            {
                workCount += taskQueueContext.getLockedTasksOperator().doWork();
            }
        }

        return workCount;
    }


}
