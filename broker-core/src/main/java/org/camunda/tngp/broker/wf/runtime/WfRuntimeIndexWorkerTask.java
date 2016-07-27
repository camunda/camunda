package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.broker.wf.WfWorkerContext;
import org.camunda.tngp.transport.requestresponse.server.WorkerTask;

public class WfRuntimeIndexWorkerTask implements WorkerTask<WfWorkerContext>
{

    @Override
    public int execute(WfWorkerContext context)
    {
        int workCount = 0;

        final WfRuntimeContext[] wfRuntimeContexts = context.getWfRuntimeManager().getContexts();

        for (int i = 0; i < wfRuntimeContexts.length; i++)
        {
            workCount += wfRuntimeContexts[i].getIndexWriter().indexLogEntries();
        }

        return workCount;
    }

}
