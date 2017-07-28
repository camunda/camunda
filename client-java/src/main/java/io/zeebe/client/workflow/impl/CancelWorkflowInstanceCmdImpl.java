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

import io.zeebe.client.event.WorkflowInstanceEvent;
import io.zeebe.client.event.impl.EventImpl;
import io.zeebe.client.impl.RequestManager;
import io.zeebe.client.impl.cmd.CommandImpl;
import io.zeebe.util.EnsureUtil;

public class CancelWorkflowInstanceCmdImpl extends CommandImpl<WorkflowInstanceEvent>
{
    private final WorkflowInstanceEventImpl workflowInstanceEvent;

    public CancelWorkflowInstanceCmdImpl(final RequestManager commandManager, WorkflowInstanceEvent baseEvent)
    {
        super(commandManager);
        EnsureUtil.ensureNotNull("base event", baseEvent);
        this.workflowInstanceEvent = new WorkflowInstanceEventImpl((WorkflowInstanceEventImpl) baseEvent,
                WorkflowInstanceEventType.CANCEL_WORKFLOW_INSTANCE.name());
    }

    @Override
    public EventImpl getEvent()
    {
        return workflowInstanceEvent;
    }

    @Override
    public String getExpectedStatus()
    {
        return WorkflowInstanceEventType.WORKFLOW_INSTANCE_CANCELED.name();
    }
}
