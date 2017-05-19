package org.camunda.tngp.client.workflow.impl;

import org.camunda.tngp.client.workflow.cmd.WorkflowInstance;

/**
 * Represents an instance of an deployed workflow, also called workflow instance.
 */
public class WorkflowInstanceImpl implements WorkflowInstance
{
    protected String bpmnProcessId;
    protected long workflowInstanceKey;
    protected int version;
    protected byte[] payload;

    public WorkflowInstanceImpl(WorkflowInstanceEvent event)
    {
        this.bpmnProcessId = event.getBpmnProcessId();
        this.version = event.getVersion();
        this.workflowInstanceKey = event.getWorkflowInstanceKey();
        this.payload = event.getPayload();
    }

    @Override
    public String getBpmnProcessId()
    {
        return bpmnProcessId;
    }

    public void setBpmnProcessId(String bpmnProcessId)
    {
        this.bpmnProcessId = bpmnProcessId;
    }

    @Override
    public long getWorkflowInstanceKey()
    {
        return workflowInstanceKey;
    }

    public void setWorkflowInstanceKey(long workflowInstanceKey)
    {
        this.workflowInstanceKey = workflowInstanceKey;
    }

    @Override
    public int getVersion()
    {
        return version;
    }

    public void setVersion(int version)
    {
        this.version = version;
    }

    @Override
    public byte[] getPayload()
    {
        return payload;
    }

    public void setPayload(byte[] payload)
    {
        this.payload = payload;
    }
}
