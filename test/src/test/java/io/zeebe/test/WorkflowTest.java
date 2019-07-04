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
import io.zeebe.client.api.response.WorkflowInstanceEvent;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class WorkflowTest {
  @Rule public final ZeebeTestRule testRule = new ZeebeTestRule();

  private ZeebeClient client;

  @Before
  public void deploy() {
    client = testRule.getClient();

    client.newDeployCommand().addResourceFromClasspath("process.bpmn").send().join();

    RecordingExporter.deploymentRecords(DeploymentIntent.CREATED).getFirst();
  }

  @Test
  public void shouldCompleteWorkflowInstance() {
    final WorkflowInstanceEvent workflowInstance =
        client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();

    client
        .newWorker()
        .jobType("task")
        .handler((c, j) -> c.newCompleteCommand(j.getKey()).send().join())
        .name("test")
        .open();

    ZeebeTestRule.assertThat(workflowInstance)
        .isEnded()
        .hasPassed("start", "task", "end")
        .hasEntered("task")
        .hasCompleted("task");
  }

  @Test
  public void shouldCompleteWorkflowInstanceWithVariables() {
    final WorkflowInstanceEvent workflowInstance =
        client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();

    final Map<String, Object> variables = new HashMap<>();
    variables.put("a", "foo");
    variables.put("b", 123);
    variables.put("c", true);
    variables.put("d", null);

    client
        .newWorker()
        .jobType("task")
        .handler((c, j) -> c.newCompleteCommand(j.getKey()).variables(variables).send().join())
        .name("test")
        .open();

    ZeebeTestRule.assertThat(workflowInstance)
        .isEnded()
        .hasVariable("a", "foo")
        .hasVariable("b", 123)
        .hasVariable("c", true)
        .hasVariable("d", null);
  }
}
