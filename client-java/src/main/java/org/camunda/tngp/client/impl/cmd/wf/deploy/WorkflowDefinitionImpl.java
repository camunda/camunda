package org.camunda.tngp.client.impl.cmd.wf.deploy;

import org.camunda.tngp.client.cmd.WorkflowDefinition;

public class WorkflowDefinitionImpl implements WorkflowDefinition
{
    protected long id;

    public WorkflowDefinitionImpl(long workflowTypeId)
    {
        this.id = workflowTypeId;
    }

    @Override
    public long getId()
    {
        return id;
    }

}
