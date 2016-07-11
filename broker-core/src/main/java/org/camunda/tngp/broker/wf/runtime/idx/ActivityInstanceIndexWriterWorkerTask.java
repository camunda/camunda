package org.camunda.tngp.broker.wf.runtime.idx;

import org.camunda.tngp.broker.wf.WfWorkerContext;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.transport.requestresponse.server.WorkerTask;

public class ActivityInstanceIndexWriterWorkerTask implements WorkerTask<WfWorkerContext>
{
    @Override
    public int execute(WfWorkerContext context)
    {
        // TODO: contract must be: This index have been written as soon as the task is pollable
        final int maxEntries = Integer.MAX_VALUE;

        int workCount = 0;

        final WfRuntimeContext[] wfRuntimeContexts = context.getWfRuntimeManager().getContexts();

        for (int i = 0; i < wfRuntimeContexts.length; i++)
        {
            workCount += wfRuntimeContexts[i].getActivityInstanceIndexWriter().update(maxEntries);
        }

        return workCount;
    }

}
