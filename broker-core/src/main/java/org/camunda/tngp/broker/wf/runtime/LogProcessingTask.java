package org.camunda.tngp.broker.wf.runtime;

import java.util.List;

import org.camunda.tngp.broker.log.LogConsumer;
import org.camunda.tngp.broker.wf.WfWorkerContext;
import org.camunda.tngp.broker.wf.repository.WfRepositoryContext;
import org.camunda.tngp.transport.requestresponse.server.WorkerTask;

public class LogProcessingTask implements WorkerTask<WfWorkerContext>
{

    @Override
    public int execute(WfWorkerContext context)
    {
        final WfRepositoryContext[] repositoryContexts = context.getWfRepositoryManager().getContexts();
        for (int i = 0; i < repositoryContexts.length; i++)
        {
            final WfRepositoryContext repositoryContext = repositoryContexts[i];
            repositoryContext.getLogConsumer().doConsume();
        }

        final WfRuntimeContext[] runtimeContexts = context.getWfRuntimeManager().getContexts();
        for (int i = 0; i < runtimeContexts.length; i++)
        {
            final WfRuntimeContext runtimeContext = runtimeContexts[i];
            runtimeContext.getLogConsumer().doConsume();
        }

        final List<LogConsumer> sourceLogConsumers = context.getWfRuntimeManager().getInputLogConsumers();

        for (int i = 0; i < sourceLogConsumers.size(); i++)
        {
            final LogConsumer sourceLogConsumer = sourceLogConsumers.get(i);
            sourceLogConsumer.doConsume();
        }

        return 0;
    }

}
