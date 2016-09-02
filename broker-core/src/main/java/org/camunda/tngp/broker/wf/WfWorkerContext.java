package org.camunda.tngp.broker.wf;

import org.camunda.tngp.broker.wf.runtime.WfRuntimeManager;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestWorkerContext;

public class WfWorkerContext extends AsyncRequestWorkerContext
{
    protected WfRuntimeManager wfRuntimeManager;

    public WfRuntimeManager getWfRuntimeManager()
    {
        return wfRuntimeManager;
    }

    public void setWfRuntimeManager(WfRuntimeManager wfRuntimeManager)
    {
        this.wfRuntimeManager = wfRuntimeManager;
    }

}
