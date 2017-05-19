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
package org.camunda.tngp.client.impl;

import org.camunda.tngp.client.WorkflowTopicClient;
import org.camunda.tngp.client.workflow.cmd.CancelWorkflowInstanceCmd;
import org.camunda.tngp.client.workflow.cmd.CreateDeploymentCmd;
import org.camunda.tngp.client.workflow.cmd.CreateWorkflowInstanceCmd;
import org.camunda.tngp.client.workflow.impl.CancelWorkflowInstanceCmdImpl;
import org.camunda.tngp.client.workflow.impl.CreateDeploymentCmdImpl;
import org.camunda.tngp.client.workflow.impl.CreateWorkflowInstanceCmdImpl;

public class WorkflowTopicClientImpl implements WorkflowTopicClient
{
    protected final TngpClientImpl client;
    protected final String topicName;
    protected final int partitionId;

    public WorkflowTopicClientImpl(final TngpClientImpl client, final String topicName, final int partitionId)
    {
        this.client = client;
        this.topicName = topicName;
        this.partitionId = partitionId;
    }

    @Override
    public CreateDeploymentCmd deploy()
    {
        return new CreateDeploymentCmdImpl(client.getCmdExecutor(), client.getObjectMapper(), topicName, partitionId);
    }

    @Override
    public CreateWorkflowInstanceCmd create()
    {
        return new CreateWorkflowInstanceCmdImpl(client.getCmdExecutor(), client.getObjectMapper(), topicName, partitionId);
    }

    @Override
    public CancelWorkflowInstanceCmd cancel()
    {
        return new CancelWorkflowInstanceCmdImpl(client.getCmdExecutor(), client.getObjectMapper(), topicName, partitionId);
    }

}
