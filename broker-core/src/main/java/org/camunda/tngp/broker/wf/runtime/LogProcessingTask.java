package org.camunda.tngp.broker.wf.runtime;

import java.util.List;

import org.camunda.tngp.broker.log.LogConsumer;
import org.camunda.tngp.broker.wf.WfWorkerContext;
import org.camunda.tngp.transport.requestresponse.server.WorkerTask;

public class LogProcessingTask implements WorkerTask<WfWorkerContext>
{

    @Override
    public int execute(WfWorkerContext context)
    {
        int workCount = 0;

        final WfRuntimeContext[] runtimeContexts = context.getWfRuntimeManager().getContexts();
        for (int i = 0; i < runtimeContexts.length; i++)
        {
            final WfRuntimeContext runtimeContext = runtimeContexts[i];
            workCount += runtimeContext.getLogConsumer().doConsume();
        }

        final List<LogConsumer> sourceLogConsumers = context.getWfRuntimeManager().getInputLogConsumers();

        for (int i = 0; i < sourceLogConsumers.size(); i++)
        {
            final LogConsumer sourceLogConsumer = sourceLogConsumers.get(i);
            workCount += sourceLogConsumer.doConsume();
        }

        return workCount;
    }
}
