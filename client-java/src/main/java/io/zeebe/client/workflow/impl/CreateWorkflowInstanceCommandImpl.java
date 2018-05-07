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

import java.io.InputStream;

import io.zeebe.client.event.WorkflowInstanceEvent;
import io.zeebe.client.event.impl.EventImpl;
import io.zeebe.client.impl.RequestManager;
import io.zeebe.client.impl.cmd.CommandImpl;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.workflow.cmd.CreateWorkflowInstanceCommand;

/**
 * Represents a command to create a workflow instance.
 */
public class CreateWorkflowInstanceCommandImpl extends CommandImpl<WorkflowInstanceEvent> implements CreateWorkflowInstanceCommand
{
    private final WorkflowInstanceEventImpl workflowInstanceEvent;

    public CreateWorkflowInstanceCommandImpl(final RequestManager commandManager,
            MsgPackConverter converter,
            String topic)
    {
        super(commandManager);

        workflowInstanceEvent = new WorkflowInstanceEventImpl(
                WorkflowInstanceEventType.CREATE_WORKFLOW_INSTANCE.name(),
                converter);
        workflowInstanceEvent.setTopicName(topic);
    }

    @Override
    public CreateWorkflowInstanceCommand payload(final InputStream payload)
    {
        this.workflowInstanceEvent.setPayloadAsJson(payload);
        return this;
    }

    @Override
    public CreateWorkflowInstanceCommand payload(final String payload)
    {
        this.workflowInstanceEvent.setPayloadAsJson(payload);
        return this;
    }

    @Override
    public CreateWorkflowInstanceCommand bpmnProcessId(final String id)
    {
        this.workflowInstanceEvent.setBpmnProcessId(id);
        return this;
    }

    @Override
    public CreateWorkflowInstanceCommand version(final int version)
    {
        this.workflowInstanceEvent.setVersion(version);
        return this;
    }

    @Override
    public CreateWorkflowInstanceCommand latestVersion()
    {
        return version(LATEST_VERSION);
    }

    @Override
    public CreateWorkflowInstanceCommand latestVersionForceRefresh()
    {
        return version(LATEST_VERSION_FORCE);
    }

    @Override
    public CreateWorkflowInstanceCommand workflowKey(long workflowKey)
    {
        this.workflowInstanceEvent.setWorkflowKey(workflowKey);
        return this;
    }

    @Override
    public EventImpl getEvent()
    {
        return workflowInstanceEvent;
    }

    @Override
    public String getExpectedStatus()
    {
        return WorkflowInstanceEventType.WORKFLOW_INSTANCE_CREATED.name();
    }

    @Override
    public String generateError(WorkflowInstanceEvent request, WorkflowInstanceEvent responseEvent)
    {
        if (workflowInstanceEvent.getWorkflowKey() >= 0)
        {
            return String.format("Failed to create instance of workflow with key '%s'", workflowInstanceEvent.getWorkflowKey());
        }
        else if (workflowInstanceEvent.getBpmnProcessId() != null)
        {
            return String.format("Failed to create instance of workflow " +
                    "with BPMN process id '%s' and version '%s'.", workflowInstanceEvent.getBpmnProcessId(), responseEvent.getVersion());
        }
        else
        {
            return super.generateError(request, responseEvent);
        }

    }

}
