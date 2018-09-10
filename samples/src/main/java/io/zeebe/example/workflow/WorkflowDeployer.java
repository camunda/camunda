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
package io.zeebe.example.workflow;

import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.ZeebeClientBuilder;
import io.zeebe.gateway.api.clients.WorkflowClient;
import io.zeebe.gateway.api.events.DeploymentEvent;

public class WorkflowDeployer {

  public static void main(final String[] args) {
    final String broker = "localhost:26501";

    final ZeebeClientBuilder clientBuilder =
        ZeebeClient.newClientBuilder().brokerContactPoint(broker);

    try (final ZeebeClient client = clientBuilder.build()) {
      final WorkflowClient workflowClient = client.workflowClient();

      final DeploymentEvent deploymentEvent =
          workflowClient
              .newDeployCommand()
              .addResourceFromClasspath("demoProcess.bpmn")
              .send()
              .join();

      System.out.println(deploymentEvent.getState());
    }
  }
}
