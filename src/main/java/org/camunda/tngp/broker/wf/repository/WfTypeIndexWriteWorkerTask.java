package org.camunda.tngp.broker.wf.repository;

import org.camunda.tngp.broker.wf.WfWorkerContext;
import org.camunda.tngp.transport.requestresponse.server.WorkerTask;

public class WfTypeIndexWriteWorkerTask implements WorkerTask<WfWorkerContext>
{

    @Override
    public int execute(WfWorkerContext context)
    {
        final int maxEntries = context.getResponsePool().getCapacity();

        int workCount = 0;

        WfRepositoryContext[] contexts = context.getWfRepositoryManager().getContexts();

        for (int i = 0; i < contexts.length; i++)
        {
            workCount += contexts[i].getWfTypeIndexWriter().update(maxEntries);
        }

        return workCount;
    }

}
