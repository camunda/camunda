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

import io.zeebe.client.WorkflowTopicClient;
import io.zeebe.client.workflow.cmd.*;
import io.zeebe.client.workflow.impl.*;

public class WorkflowTopicClientImpl implements WorkflowTopicClient
{
    protected final ZeebeClientImpl client;
    protected final Topic topic;

    public WorkflowTopicClientImpl(final ZeebeClientImpl client, final String topicName, final int partitionId)
    {
        this.client = client;
        this.topic = new Topic(topicName, partitionId);
    }

    @Override
    public CreateDeploymentCmd deploy()
    {
        return new CreateDeploymentCmdImpl(client.getCommandManager(), client.getObjectMapper(), client.getMsgPackConverter(), topic);
    }

    @Override
    public CreateWorkflowInstanceCmd create()
    {
        return new CreateWorkflowInstanceCmdImpl(client.getCommandManager(), client.getObjectMapper(), client.getMsgPackConverter(), topic);
    }

    @Override
    public CancelWorkflowInstanceCmd cancel()
    {
        return new CancelWorkflowInstanceCmdImpl(client.getCommandManager(), client.getObjectMapper(), client.getMsgPackConverter(), topic);
    }

    @Override
    public UpdatePayloadCmd updatePayload()
    {
        return new UpdatePayloadCmdImpl(client.getCommandManager(), client.getObjectMapper(), client.getMsgPackConverter(), topic);
    }

}
