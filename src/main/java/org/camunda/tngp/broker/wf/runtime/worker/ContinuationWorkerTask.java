package org.camunda.tngp.broker.wf.runtime.worker;

import org.camunda.tngp.broker.wf.WfWorkerContext;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeManager;
import org.camunda.tngp.transport.requestresponse.server.WorkerTask;

public class ContinuationWorkerTask implements WorkerTask<WfWorkerContext>
{
    @Override
    public int execute(WfWorkerContext context)
    {
        final WfRuntimeManager wfRuntimeManager = context.getWfRuntimeManager();
        context.get
        return 0;
    }

}
