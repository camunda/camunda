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

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import io.zeebe.client.event.DeploymentEvent;
import io.zeebe.client.event.TopicEventType;
import io.zeebe.client.event.WorkflowDefinition;
import io.zeebe.client.event.impl.EventImpl;

public class DeploymentEventImpl extends EventImpl implements DeploymentEvent
{
    private byte[] bpmnXml;

    private List<WorkflowDefinition> deployedWorkflows;

    private String errorMessage;

    @JsonCreator
    public DeploymentEventImpl(@JsonProperty("state") String state)
    {
        super(TopicEventType.DEPLOYMENT, state);
    }

    public byte[] getBpmnXml()
    {
        return bpmnXml;
    }

    public void setBpmnXml(byte[] bpmnXml)
    {
        this.bpmnXml = bpmnXml;
    }

    @JsonTypeInfo(use = Id.NAME, defaultImpl = WorkflowDefinitionImpl.class)
    public List<WorkflowDefinition> getDeployedWorkflows()
    {
        return deployedWorkflows;
    }

    public void setDeployedWorkflows(List<WorkflowDefinition> deployedWorkflows)
    {
        this.deployedWorkflows = deployedWorkflows;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("DeploymentEvent [bpmnXml=");
        builder.append(new String(bpmnXml, StandardCharsets.UTF_8));
        builder.append(", deployedWorkflows=");
        builder.append(deployedWorkflows);
        builder.append(", errorMessage=");
        builder.append(errorMessage);
        builder.append("]");
        return builder.toString();
    }
}
