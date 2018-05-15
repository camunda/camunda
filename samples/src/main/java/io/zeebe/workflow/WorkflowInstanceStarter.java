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
package io.zeebe.workflow;

import java.util.stream.Collectors;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.clients.WorkflowClient;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.cmd.ClientCommandRejectedException;

public class WorkflowInstanceStarter
{

    public static void main(String[] args)
    {
        final String brokerContactPoint = "127.0.0.1:51015";
        final String bpmnProcessId = "demoProcess";
        final String topicName = "default-topic";
        final int partitionId = 0;

        final ZeebeClient zeebeClient = ZeebeClient.newClient()
            .brokerContactPoint(brokerContactPoint)
            .create();

        System.out.println(String.format("> Connecting to %s", brokerContactPoint));

        System.out.println(String.format("> Deploying workflow to topic '%s' and partition '%d'", topicName, partitionId));

        final WorkflowClient workflowClient = zeebeClient.topicClient(topicName).workflowClient();

        final DeploymentEvent deploymentResult = workflowClient
            .newDeployCommand()
            .addResourceFromClasspath("demoProcess.bpmn")
            .send()
            .join();

        try
        {
            final String deployedWorkflows = deploymentResult.getDeployedWorkflows().stream()
                    .map(wf -> String.format("<%s:%d>", wf.getBpmnProcessId(), wf.getVersion()))
                    .collect(Collectors.joining(","));

            System.out.println(String.format("> Deployed: %s", deployedWorkflows));

            System.out.println(String.format("> Create workflow instance for workflow: %s", bpmnProcessId));

            workflowClient
                .newCreateInstanceCommand()
                .bpmnProcessId(bpmnProcessId)
                .latestVersion()
                .payload("{\"a\": \"b\"}")
                .send()
                .join();

            System.out.println("> Created.");
        }
        catch (ClientCommandRejectedException exception)
        {
            System.out.println(String.format("> Fail to deploy: %s", exception.getMessage()));
        }

        System.out.println("> Closing...");

        zeebeClient.close();

        System.out.println("> Closed.");
    }

}
