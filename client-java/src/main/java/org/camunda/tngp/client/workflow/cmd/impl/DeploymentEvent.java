/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.client.workflow.cmd.impl;

import java.util.List;

public class DeploymentEvent
{
    private DeploymentEventType event;
    private String bpmnXml;

    private List<DeployedWorkflow> deployedWorkflows;

    private String errorMessage;

    public DeploymentEventType getEvent()
    {
        return event;
    }

    public void setEvent(DeploymentEventType event)
    {
        this.event = event;
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
        event = null;
        bpmnXml = null;
        deployedWorkflows = null;
        errorMessage = null;
    }
}
