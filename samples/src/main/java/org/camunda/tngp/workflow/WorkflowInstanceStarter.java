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
package org.camunda.tngp.workflow;

import java.io.InputStream;
import java.util.Properties;
import java.util.stream.Collectors;

import org.camunda.tngp.client.ClientProperties;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.client.workflow.cmd.DeploymentResult;

public class WorkflowInstanceStarter
{

    public static void main(String[] args)
    {
        final String brokerContactPoint = "127.0.0.1:51015";
        final InputStream bpmnStream = WorkflowInstanceStarter.class.getResourceAsStream("/demoProcess.bpmn");
        final String bpmnProcessId = "demoProcess";
        final String topicName = "default-topic";
        final int partitionId = 0;

        final Properties clientProperties = new Properties();
        clientProperties.put(ClientProperties.BROKER_CONTACTPOINT, brokerContactPoint);

        final TngpClient tngpClient = new TngpClientImpl(clientProperties);

        System.out.println(String.format("> Connecting to %s ...", brokerContactPoint));
        tngpClient.connect();

        System.out.println("> Connected.");

        System.out.println(String.format("> Deploying workflow to topic '%s' and partition '%d'", topicName, partitionId));

        final DeploymentResult deploymentResult = tngpClient.workflowTopic(topicName, partitionId)
            .deploy()
            .resourceStream(bpmnStream)
            .execute();

        if (deploymentResult.isDeployed())
        {
            final String deployedWorkflows = deploymentResult.getDeployedWorkflows().stream()
                    .map(wf -> String.format("<%s:%d>", wf.getBpmnProcessId(), wf.getVersion()))
                    .collect(Collectors.joining(","));

            System.out.println(String.format("> Deployed: %s", deployedWorkflows));

            System.out.println(String.format("> Create workflow instance for workflow: %s", bpmnProcessId));

            tngpClient.workflowTopic(topicName, partitionId)
                .create()
                .bpmnProcessId(bpmnProcessId)
                .execute();

            System.out.println("> Created.");

        }
        else
        {
            System.out.println(String.format("> Fail to deploy: %s", deploymentResult.getErrorMessage()));
        }

        System.out.println("> Closing...");

        tngpClient.close();

        System.out.println("> Closed.");
    }

}
