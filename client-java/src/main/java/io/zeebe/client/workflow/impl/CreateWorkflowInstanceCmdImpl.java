package io.zeebe.client.workflow.impl;

import static io.zeebe.util.EnsureUtil.*;

import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.impl.ClientCommandManager;
import io.zeebe.client.impl.Topic;
import io.zeebe.client.impl.cmd.AbstractExecuteCmdImpl;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.workflow.cmd.CreateWorkflowInstanceCmd;
import io.zeebe.client.workflow.cmd.WorkflowInstance;
import io.zeebe.protocol.clientapi.EventType;

/**
 * Represents a command to create a workflow instance.
 */
public class CreateWorkflowInstanceCmdImpl extends AbstractExecuteCmdImpl<WorkflowInstanceEvent, WorkflowInstance> implements CreateWorkflowInstanceCmd
{
    private static final String EXCEPTION_MSG = "Failed to create instance of workflow with BPMN process id '%s' and version '%d'.";

    private final WorkflowInstanceEvent workflowInstanceEvent = new WorkflowInstanceEvent();
    protected final MsgPackConverter msgPackConverter = new MsgPackConverter();

    public CreateWorkflowInstanceCmdImpl(final ClientCommandManager commandManager, final ObjectMapper objectMapper, final Topic topic)
    {
        super(commandManager, objectMapper, topic, WorkflowInstanceEvent.class, EventType.WORKFLOW_EVENT);
    }

    @Override
    public CreateWorkflowInstanceCmd payload(final InputStream payload)
    {
        this.workflowInstanceEvent.setPayload(msgPackConverter.convertToMsgPack(payload));
        return this;
    }

    @Override
    public CreateWorkflowInstanceCmd payload(final String payload)
    {
        this.workflowInstanceEvent.setPayload(msgPackConverter.convertToMsgPack(payload));
        return this;
    }

    @Override
    public CreateWorkflowInstanceCmd bpmnProcessId(final String id)
    {
        this.workflowInstanceEvent.setBpmnProcessId(id);
        return this;
    }

    @Override
    public CreateWorkflowInstanceCmd version(final int version)
    {
        this.workflowInstanceEvent.setVersion(version);
        return this;
    }

    @Override
    public CreateWorkflowInstanceCmd latestVersion()
    {
        return version(LATEST_VERSION);
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
    protected WorkflowInstance getResponseValue(final long key, final WorkflowInstanceEvent event)
    {
        if (event.getEventType() == WorkflowInstanceEventType.WORKFLOW_INSTANCE_REJECTED)
        {
            throw new ClientCommandRejectedException(String.format(EXCEPTION_MSG, event.getBpmnProcessId(), event.getVersion()));
        }
        return new WorkflowInstanceImpl(event);
    }

    @Override
    public void validate()
    {
        super.validate();
        ensureNotNullOrEmpty("bpmnProcessId", workflowInstanceEvent.getBpmnProcessId());
        ensureGreaterThanOrEqual("version", workflowInstanceEvent.getVersion(), LATEST_VERSION);
    }
}
