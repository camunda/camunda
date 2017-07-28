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
package io.zeebe.client.impl;

import io.zeebe.client.WorkflowsClient;
import io.zeebe.client.cmd.Request;
import io.zeebe.client.event.WorkflowInstanceEvent;
import io.zeebe.client.workflow.cmd.CreateDeploymentCommand;
import io.zeebe.client.workflow.cmd.CreateWorkflowInstanceCommand;
import io.zeebe.client.workflow.cmd.UpdatePayloadCommand;
import io.zeebe.client.workflow.impl.CancelWorkflowInstanceCmdImpl;
import io.zeebe.client.workflow.impl.CreateDeploymentCommandImpl;
import io.zeebe.client.workflow.impl.CreateWorkflowInstanceCommandImpl;
import io.zeebe.client.workflow.impl.UpdatePayloadCommandImpl;

public class WorkflowsClientImpl implements WorkflowsClient
{
    protected final ZeebeClientImpl client;

    public WorkflowsClientImpl(final ZeebeClientImpl client)
    {
        this.client = client;
    }

    @Override
    public CreateDeploymentCommand deploy(String topic)
    {
        return new CreateDeploymentCommandImpl(client.getCommandManager(), topic);
    }

    @Override
    public CreateWorkflowInstanceCommand create(String topic)
    {
        return new CreateWorkflowInstanceCommandImpl(client.getCommandManager(), client.getMsgPackConverter(), topic);
    }

    @Override
    public Request<WorkflowInstanceEvent> cancel(WorkflowInstanceEvent baseEvent)
    {
        return new CancelWorkflowInstanceCmdImpl(client.getCommandManager(), baseEvent);
    }

    @Override
    public UpdatePayloadCommand updatePayload(WorkflowInstanceEvent baseEvent)
    {
        return new UpdatePayloadCommandImpl(client.getCommandManager(), baseEvent);
    }

}
