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

import java.util.List;

public class DeploymentEvent
{
    private DeploymentEventType eventType;
    private String bpmnXml;

    private List<DeployedWorkflow> deployedWorkflows;

    private String errorMessage;

    public DeploymentEventType getEventType()
    {
        return eventType;
    }

    public void setEventType(DeploymentEventType eventType)
    {
        this.eventType = eventType;
    }

    public String getBpmnXml()
    {
        return bpmnXml;
    }

    public void setBpmnXml(String bpmnXml)
    {
        this.bpmnXml = bpmnXml;
    }

    public List<DeployedWorkflow> getDeployedWorkflows()
    {
        return deployedWorkflows;
    }

    public void setDeployedWorkflows(List<DeployedWorkflow> deployedWorkflows)
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

    public void reset()
    {
        eventType = null;
        bpmnXml = null;
        deployedWorkflows = null;
        errorMessage = null;
    }
}
