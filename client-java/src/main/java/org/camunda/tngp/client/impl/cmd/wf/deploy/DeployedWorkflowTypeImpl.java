package org.camunda.tngp.client.impl.cmd.wf.deploy;

import org.camunda.tngp.client.cmd.DeployedWorkflowType;

public class DeployedWorkflowTypeImpl implements DeployedWorkflowType
{
    protected long workflowTypeId;

    public DeployedWorkflowTypeImpl(long workflowTypeId)
    {
        this.workflowTypeId = workflowTypeId;
    }

    @Override
    public long getWorkflowTypeId()
    {
        return workflowTypeId;
    }

}
