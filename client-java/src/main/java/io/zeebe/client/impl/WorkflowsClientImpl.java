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

import io.zeebe.client.api.clients.WorkflowClient;
import io.zeebe.client.api.commands.*;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.impl.workflow.*;

public class WorkflowsClientImpl implements WorkflowClient
{
    private final TopicClientImpl client;

    public WorkflowsClientImpl(final TopicClientImpl client)
    {
        this.client = client;
    }

    @Override
    public DeployWorkflowCommandStep1 newDeployCommand()
    {
        return new DeployWorkflowCommandImpl(client.getCommandManager(), client.getTopic());
    }

    @Override
    public CreateWorkflowInstanceCommandStep1 newCreateInstanceCommand()
    {
        return new CreateWorkflowInstanceCommandImpl(client.getCommandManager(), client.getMsgPackConverter(), client.getTopic());
    }

    @Override
    public CancelWorkflowInstanceCommandStep1 newCancelInstanceCommand(WorkflowInstanceEvent event)
    {
        return new CancelWorkflowInstanceCommandImpl(client.getCommandManager(), event);
    }

    @Override
    public UpdatePayloadWorkflowInstanceCommandStep1 newUpdatePayloadCommand(WorkflowInstanceEvent event)
    {
        return new UpdatePayloadCommandImpl(client.getCommandManager(), event);
    }

}
