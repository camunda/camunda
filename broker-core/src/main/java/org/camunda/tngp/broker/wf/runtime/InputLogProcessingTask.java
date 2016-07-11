package org.camunda.tngp.broker.wf.runtime;

import java.util.List;

import org.camunda.tngp.broker.log.LogEntryProcessor;
import org.camunda.tngp.broker.wf.WfWorkerContext;
import org.camunda.tngp.transport.requestresponse.server.WorkerTask;

public class InputLogProcessingTask implements WorkerTask<WfWorkerContext>
{

    @Override
    public int execute(WfWorkerContext context)
    {
        final List<LogEntryProcessor<?>> sourceLogs = context.getWfRuntimeManager().getInputLogProcessors();

        for (int i = 0; i < sourceLogs.size(); i++)
        {
            final LogEntryProcessor<?> logEntryProcessor = sourceLogs.get(i);
            logEntryProcessor.doWorkSingle();
        }

        return 0;
    }

}
