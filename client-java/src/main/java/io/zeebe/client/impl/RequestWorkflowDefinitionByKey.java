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

import io.zeebe.client.api.commands.Workflow;
import io.zeebe.client.impl.clustering.ClientTopologyManager;
import io.zeebe.client.impl.event.WorkflowImpl;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageType;

public class RequestWorkflowDefinitionByKey extends ControlMessageRequest<Workflow>
{
    private RequestWorkflowDefinitionRequest request = new RequestWorkflowDefinitionRequest();

    public RequestWorkflowDefinitionByKey(RequestManager commandManager, ClientTopologyManager topologyListener, long key)
    {
        super(commandManager, ControlMessageType.GET_WORKFLOW, Workflow.class);
        setTargetPartition(Protocol.SYSTEM_PARTITION);
        request.setWorkflowKey(key);
    }

    @Override
    public Class<? extends Workflow> getResponseClass()
    {
        return WorkflowImpl.class;
    }

    @Override
    public Object getRequest()
    {
        return request;
    }
}
