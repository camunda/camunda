package org.camunda.tngp.broker.wf.runtime.worker;

import org.camunda.tngp.broker.wf.WfWorkerContext;
import org.camunda.tngp.broker.wf.runtime.BpmnEventHandler;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeManager;
import org.camunda.tngp.transport.requestresponse.server.WorkerTask;

public class ContinuationWorkerTask implements WorkerTask<WfWorkerContext>
{

    @Override
    public int execute(WfWorkerContext context)
    {
        final WfRuntimeManager wfRuntimeManager = context.getWfRuntimeManager();
        final WfRuntimeContext[] contexts = wfRuntimeManager.getContexts();

        final int workCount = 0;

        for (int i = 0; i < contexts.length; i++)
        {
            final WfRuntimeContext runtimeContext = contexts[i];
            workCount += handleContinuation(runtimeContext);
        }

        return workCount;
    }

    protected int handleContinuation(WfRuntimeContext runtimeContext)
    {

        return runtimeContext.getBpmnEventHandler().doWork();
    }

}
