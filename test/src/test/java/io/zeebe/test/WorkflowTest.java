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
package io.zeebe.test;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class WorkflowTest {
  @Rule public final ZeebeTestRule testRule = new ZeebeTestRule();

  private ZeebeClient client;
  private String topic;

  @Before
  public void deploy() {
    client = testRule.getClient();
    topic = testRule.getDefaultTopic();

    client
        .topicClient(topic)
        .workflowClient()
        .newDeployCommand()
        .addResourceFromClasspath("process.bpmn")
        .send()
        .join();
  }

  @Test
  public void shouldCompleteWorkflowInstance() {
    final WorkflowInstanceEvent workflowInstance =
        client
            .topicClient(topic)
            .workflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    client
        .topicClient(topic)
        .jobClient()
        .newWorker()
        .jobType("task")
        .handler((c, j) -> c.newCompleteCommand(j).withoutPayload().send().join())
        .name("test")
        .open();

    testRule.waitUntilWorkflowInstanceCompleted(workflowInstance.getWorkflowInstanceKey());
  }
}
