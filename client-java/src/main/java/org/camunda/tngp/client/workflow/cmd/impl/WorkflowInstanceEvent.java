package org.camunda.tngp.client.workflow.cmd.impl;

/**
 *
 */
public class WorkflowInstanceEvent
{
    protected String bpmnProcessId;
    protected int version = -1;
    protected long workflowInstanceKey;
    protected WorkflowInstanceEventType eventType;

    public String getBpmnProcessId()
    {
        return bpmnProcessId;
    }

    public void setBpmnProcessId(String bpmnProcessId)
    {
        this.bpmnProcessId = bpmnProcessId;
    }

    public int getVersion()
    {
        return version;
    }

    public void setVersion(int version)
    {
        this.version = version;
    }

    public WorkflowInstanceEventType getEventType()
    {
        return eventType;
    }

    public void setEventType(WorkflowInstanceEventType eventType)
    {
        this.eventType = eventType;
    }

    public long getWorkflowInstanceKey()
    {
        return workflowInstanceKey;
    }

    public void setWorkflowInstanceKey(long workflowInstanceKey)
    {
        this.workflowInstanceKey = workflowInstanceKey;
    }

    public void reset()
    {
        bpmnProcessId = null;
        version = -1;
        workflowInstanceKey = -1;
        eventType = null;
    }
}
