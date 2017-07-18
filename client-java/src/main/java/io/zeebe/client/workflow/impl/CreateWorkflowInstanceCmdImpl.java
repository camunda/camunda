/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.workflow.impl;

import static io.zeebe.util.EnsureUtil.ensureGreaterThanOrEqual;

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
    private static final String ILLEGAL_ARG_MSG = "Can not create a workflow instance. Need to provide either a workflow key or a BPMN process id with/without version.";
    private static final String REJECTED_BY_ID_MSG = "Failed to create instance of workflow with BPMN process id '%s' and version '%d'.";
    private static final String REJECTED_BY_KEY_MSG = "Failed to create instance of workflow with workflow key '%d'.";

    private final WorkflowInstanceEvent workflowInstanceEvent = new WorkflowInstanceEvent();
    private final MsgPackConverter msgPackConverter;

    public CreateWorkflowInstanceCmdImpl(final ClientCommandManager commandManager, final ObjectMapper objectMapper, MsgPackConverter msgPackConverter, final Topic topic)
    {
        super(commandManager, objectMapper, topic, WorkflowInstanceEvent.class, EventType.WORKFLOW_INSTANCE_EVENT);
        this.msgPackConverter = msgPackConverter;
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
    public CreateWorkflowInstanceCmd workflowKey(long workflowKey)
    {
        this.workflowInstanceEvent.setWorkflowKey(workflowKey);
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
    protected WorkflowInstance getResponseValue(final long key, final WorkflowInstanceEvent event)
    {
        if (event.getEventType() == WorkflowInstanceEventType.WORKFLOW_INSTANCE_REJECTED)
        {
            final String errMsg;
            if (event.getWorkflowKey() > 0)
            {
                errMsg = String.format(REJECTED_BY_KEY_MSG, event.getWorkflowKey());
            }
            else
            {
                errMsg = String.format(REJECTED_BY_ID_MSG, event.getBpmnProcessId(), event.getVersion());
            }
            throw new ClientCommandRejectedException(errMsg);
        }
        return new WorkflowInstanceImpl(event);
    }

    @Override
    public void validate()
    {
        super.validate();

        final String bpmnProcessId = workflowInstanceEvent.getBpmnProcessId();
        final int version = workflowInstanceEvent.getVersion();

        if (workflowInstanceEvent.getWorkflowKey() < 0)
        {
            if (bpmnProcessId == null || bpmnProcessId.isEmpty())
            {
                throw new RuntimeException(ILLEGAL_ARG_MSG);
            }
            ensureGreaterThanOrEqual("version", workflowInstanceEvent.getVersion(), LATEST_VERSION);
        }
        else
        {
            if ((bpmnProcessId != null && !bpmnProcessId.isEmpty()) || version != LATEST_VERSION)
            {
                throw new RuntimeException(ILLEGAL_ARG_MSG);
            }
        }
    }
}
