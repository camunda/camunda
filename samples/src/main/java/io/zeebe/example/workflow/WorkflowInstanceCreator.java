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
import io.zeebe.client.api.clients.WorkflowClient;
import io.zeebe.client.api.events.WorkflowInstanceEvent;

public class WorkflowInstanceCreator {

  public static void main(String[] args) {
    final String broker = "127.0.0.1:51015";
    final String topic = "default-topic";

    final String bpmnProcessId = "demoProcess";

    final ZeebeClientBuilder builder = ZeebeClient.newClientBuilder().brokerContactPoint(broker);

    try (ZeebeClient client = builder.build()) {
      final WorkflowClient workflowClient = client.topicClient(topic).workflowClient();

      System.out.println("Creating workflow instance");

      final WorkflowInstanceEvent workflowInstanceEvent =
          workflowClient
              .newCreateInstanceCommand()
              .bpmnProcessId(bpmnProcessId)
              .latestVersion()
              .send()
              .join();

      System.out.println(workflowInstanceEvent.getState());
    }
  }
}
