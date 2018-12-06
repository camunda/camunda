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

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;
import io.zeebe.client.api.commands.WorkflowResource;
import io.zeebe.client.api.commands.Workflows;

public class DeploymentViewer {

  public static void main(final String[] args) {

    final String broker = "localhost:26500";

    final ZeebeClientBuilder clientBuilder =
        ZeebeClient.newClientBuilder().brokerContactPoint(broker);

    try (ZeebeClient client = clientBuilder.build()) {

      final Workflows workflows = client.newWorkflowRequest().send().join();

      System.out.println("Printing all deployed workflows:");

      workflows
          .getWorkflows()
          .forEach(
              wf -> {
                System.out.println("Workflow resource for " + wf + ":");

                final WorkflowResource resource =
                    client.newResourceRequest().workflowKey(wf.getWorkflowKey()).send().join();

                System.out.println(resource);
              });

      System.out.println("Done");
    }
  }
}
