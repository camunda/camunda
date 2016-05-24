package org.camunda.tngp.broker.wf;

import org.camunda.tngp.broker.wf.repository.WfRepositoryManager;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeManager;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestWorkerContext;

public class WfWorkerContext extends AsyncRequestWorkerContext
{
    protected WfRepositoryManager wfRepositoryManager;

    protected WfRuntimeManager wfRuntimeManager;

    public WfRepositoryManager getWfRepositoryManager()
    {
        return wfRepositoryManager;
    }

    public void setWfRepositoryManager(WfRepositoryManager wfRepositoryManager)
    {
        this.wfRepositoryManager = wfRepositoryManager;
    }

    public WfRuntimeManager getWfRuntimeManager()
    {
        return wfRuntimeManager;
    }

    public void setWfRuntimeManager(WfRuntimeManager wfRuntimeManager)
    {
        this.wfRuntimeManager = wfRuntimeManager;
    }

}
