package org.camunda.tngp.broker.taskqueue;

import java.util.List;

import org.camunda.tngp.broker.services.LogEntryProcessorService.LogEntryProcessor;
import org.camunda.tngp.transport.requestresponse.server.WorkerTask;

public class InputLogProcessingTask implements WorkerTask<TaskQueueWorkerContext>
{

    @Override
    public int execute(TaskQueueWorkerContext context)
    {
        final List<LogEntryProcessor<?>> sourceLogs = context.getTaskQueueManager().getInputLogProcessors();

        for (int i = 0; i < sourceLogs.size(); i++)
        {
            final LogEntryProcessor<?> logEntryProcessor = sourceLogs.get(i);
            logEntryProcessor.doWork();
        }

        return 0;
    }

}
