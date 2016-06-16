package org.camunda.tngp.broker.wf.repository.idx;

import org.camunda.tngp.broker.wf.WfWorkerContext;
import org.camunda.tngp.broker.wf.repository.WfRepositoryContext;
import org.camunda.tngp.transport.requestresponse.server.WorkerTask;

public class WfTypeIndexWriteWorkerTask implements WorkerTask<WfWorkerContext>
{

    @Override
    public int execute(WfWorkerContext context)
    {
        int workCount = 0;

        WfRepositoryContext[] contexts = context.getWfRepositoryManager().getContexts();

        for (int i = 0; i < contexts.length; i++)
        {
            workCount += contexts[i].getWfTypeIndexWriter().update();
        }

        return workCount;
    }

}
