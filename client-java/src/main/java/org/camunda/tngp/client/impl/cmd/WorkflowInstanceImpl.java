package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.cmd.WorkflowInstance;

public class WorkflowInstanceImpl implements WorkflowInstance
{
    protected final long id;

    public WorkflowInstanceImpl(long id)
    {
        this.id = id;
    }

    @Override
    public long getId()
    {
        return id;
    }

}
