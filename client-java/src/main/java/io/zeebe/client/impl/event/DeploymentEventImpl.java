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
package io.zeebe.client.impl.event;

import java.util.List;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.zeebe.client.api.commands.Workflow;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.impl.record.DeploymentRecordImpl;
import io.zeebe.protocol.clientapi.RecordType;

public class DeploymentEventImpl extends DeploymentRecordImpl implements DeploymentEvent
{
    private List<Workflow> deployedWorkflows;

    @JsonCreator
    public DeploymentEventImpl(@JacksonInject ZeebeObjectMapper objectMapper)
    {
        super(objectMapper, RecordType.EVENT);
    }

    @Override
    @JsonDeserialize(contentAs = WorkflowImpl.class)
    public List<Workflow> getDeployedWorkflows()
    {
        return deployedWorkflows;
    }

    public void setDeployedWorkflows(List<Workflow> deployedWorkflows)
    {
        this.deployedWorkflows = deployedWorkflows;
    }

    @JsonIgnore
    @Override
    public DeploymentState getState()
    {
        return DeploymentState.valueOf(getMetadata().getIntent());
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("DeploymentEvent [state=");
        builder.append(getState());
        builder.append(", topic=");
        builder.append(getDeploymentTopic());
        builder.append(", resource=");
        builder.append(getResources());
        builder.append(", deployedWorkflows=");
        builder.append(deployedWorkflows);
        builder.append("]");
        return builder.toString();
    }

}
