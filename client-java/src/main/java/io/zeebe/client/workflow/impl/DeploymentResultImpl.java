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
package io.zeebe.client.workflow.impl;

import java.util.ArrayList;
import java.util.List;

import io.zeebe.client.workflow.cmd.DeploymentResult;
import io.zeebe.client.workflow.cmd.WorkflowDefinition;

public class DeploymentResultImpl implements DeploymentResult
{
    protected boolean isDeployed;

    protected long key;

    protected String errorMessage;

    protected List<WorkflowDefinition> deployedWorkflows = new ArrayList<>();

    @Override
    public boolean isDeployed()
    {
        return isDeployed;
    }

    @Override
    public long getKey()
    {
        return key;
    }

    @Override
    public List<WorkflowDefinition> getDeployedWorkflows()
    {
        return deployedWorkflows;
    }

    @Override
    public String getErrorMessage()
    {
        return errorMessage;
    }

    public DeploymentResultImpl setIsDeployed(boolean isDeployed)
    {
        this.isDeployed = isDeployed;
        return this;
    }

    public DeploymentResultImpl setKey(long key)
    {
        this.key = key;
        return this;
    }

    public DeploymentResultImpl setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
        return this;
    }

    public DeploymentResultImpl setDeployedWorkflows(List<WorkflowDefinition> deployedWorkflows)
    {
        this.deployedWorkflows = deployedWorkflows;
        return this;
    }
}
