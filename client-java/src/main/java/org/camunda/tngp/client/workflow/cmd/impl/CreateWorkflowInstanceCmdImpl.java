package org.camunda.tngp.client.workflow.cmd.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.AbstractExecuteCmdImpl;
import org.camunda.tngp.client.workflow.cmd.CreateWorkflowInstanceCmd;
import org.camunda.tngp.client.workflow.cmd.WorkflowInstance;
import org.camunda.tngp.client.workflow.cmd.WorkflowInstanceRejectedException;
import org.camunda.tngp.protocol.clientapi.EventType;

import java.io.InputStream;

import static org.camunda.tngp.util.EnsureUtil.ensureNotNull;

/**
 *
 */
public class CreateWorkflowInstanceCmdImpl extends AbstractExecuteCmdImpl<WorkflowInstanceEvent, WorkflowInstance> implements CreateWorkflowInstanceCmd
{
    private WorkflowInstanceEvent workflowInstanceEvent = new WorkflowInstanceEvent();

    public CreateWorkflowInstanceCmdImpl(ClientCmdExecutor cmdExecutor, ObjectMapper objectMapper, int topicId)
    {
        super(cmdExecutor, objectMapper, WorkflowInstanceEvent.class, topicId, EventType.WORKFLOW_EVENT);
    }

    @Override
    public CreateWorkflowInstanceCmd payload(InputStream payload)
    {
        // not supported yet
        return null;
    }

    @Override
    public CreateWorkflowInstanceCmd payload(String payload)
    {
        // not supported yet
        return null;
    }

    @Override
    public CreateWorkflowInstanceCmd bpmnProcessId(String id)
    {
        this.workflowInstanceEvent.setBpmnProcessId(id);
        return this;
    }

    @Override
    public CreateWorkflowInstanceCmd version(int version)
    {
        this.workflowInstanceEvent.setVersion(version);
        return this;
    }

    @Override
    protected Object writeCommand()
    {
        this.workflowInstanceEvent.setEventType(WorkflowInstanceEventType.CREATE_WORKFLOW_INSTANCE);
        return workflowInstanceEvent;
    }

    @Override
    protected long getKey()
    {
        return -1;
    }

    @Override
    protected void reset()
    {
        this.workflowInstanceEvent.reset();
    }

    @Override
    protected WorkflowInstance getResponseValue(long key, WorkflowInstanceEvent event)
    {
        if (event.getEventType() == WorkflowInstanceEventType.WORKFLOW_INSTANCE_REJECTED)
        {
            throw new WorkflowInstanceRejectedException(event.getBpmnProcessId(), event.getVersion());
        }
        return new WorkflowInstanceImpl(event);
    }

    @Override
    public void validate()
    {
        ensureNotNull("bpmnProcessId", workflowInstanceEvent.getBpmnProcessId());
    }
}
