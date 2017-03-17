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
import org.camunda.tngp.client.cmd.StartWorkflowInstanceCmd;
import org.camunda.tngp.client.workflow.cmd.CreateDeploymentCmd;
import org.camunda.tngp.client.workflow.cmd.impl.CreateDeploymentCmdImpl;

public class WorkflowTopicClientImpl implements WorkflowTopicClient
{
    protected final TngpClientImpl client;
    protected final int topicId;

    public WorkflowTopicClientImpl(TngpClientImpl client, int topicId)
    {
        this.client = client;
        this.topicId = topicId;
    }

    @Override
    public CreateDeploymentCmd deploy()
    {
        return new CreateDeploymentCmdImpl(client.getCmdExecutor(), client.getObjectMapper(), topicId);
    }

    @Override
    public StartWorkflowInstanceCmd start()
    {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("not implemented");
    }

}
