package org.camunda.tngp.broker.taskqueue;

import java.util.List;

import org.camunda.tngp.broker.log.LogConsumer;
import org.camunda.tngp.transport.requestresponse.server.WorkerTask;

public class LogProcessingTask implements WorkerTask<TaskQueueWorkerContext>
{

    @Override
    public int execute(TaskQueueWorkerContext context)
    {
        final TaskQueueContext[] taskQueueContexts = context.getTaskQueueManager().getContexts();
        for (int i = 0; i < taskQueueContexts.length; i++)
        {
            final LogConsumer logConsumer = taskQueueContexts[i].getLogConsumer();
            logConsumer.doConsume();
        }

        final List<LogConsumer> sourceLogs = context.getTaskQueueManager().getInputLogConsumers();

        for (int i = 0; i < sourceLogs.size(); i++)
        {
            final LogConsumer inputLogConsumer = sourceLogs.get(i);
            inputLogConsumer.doConsume();
        }

        return 0;
    }

}
