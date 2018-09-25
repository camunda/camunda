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
package io.zeebe.client.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Charsets;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.Workflow;
import io.zeebe.client.impl.command.WorkflowImpl;
import io.zeebe.client.util.TestEnvironmentRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.util.StreamUtil;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DeployWorkflowTest {

  public static final String BPMN_1_FILENAME = "/workflows/demo-process.bpmn";
  public static final String BPMN_2_FILENAME = "/workflows/another-demo-process.bpmn";
  public static final String YAML_FILENAME = "/workflows/simple-workflow.yaml";

  public static final String BPMN_1_PROCESS_ID = "demoProcess";
  public static final String BPMN_2_PROCESS_ID = "anotherDemoProcess";
  public static final String YAML_PROCESS_ID = "yaml-workflow";

  @Rule public TestEnvironmentRule rule = new TestEnvironmentRule();

  private ZeebeClient client;

  @Before
  public void setUp() {
    client = rule.getClient();
  }

  @Test
  public void shouldDeployWorkflowFromFile() {
    // given
    final String filename = DeployWorkflowTest.class.getResource(BPMN_1_FILENAME).getPath();
    final Workflow expected = new WorkflowImpl("demoProcess", 1, 1, filename);

    // when
    final List<Workflow> workflows =
        client
            .workflowClient()
            .newDeployCommand()
            .addResourceFile(filename)
            .send()
            .join()
            .getDeployedWorkflows();

    // then
    assertThat(workflows).containsOnly(expected);
  }

  @Test
  public void shouldDeployWorkflowFromClasspath() {
    // given
    final String filename = BPMN_1_FILENAME.substring(1);
    final Workflow expected = new WorkflowImpl(BPMN_1_PROCESS_ID, 1, 1, filename);

    // when
    final List<Workflow> workflows =
        client
            .workflowClient()
            .newDeployCommand()
            .addResourceFromClasspath(filename)
            .send()
            .join()
            .getDeployedWorkflows();

    // then
    assertThat(workflows).containsOnly(expected);
  }

  @Test
  public void shouldDeployWorkflowFromInputStream() {
    // given
    final String filename = BPMN_1_FILENAME;
    final InputStream resourceAsStream = DeployWorkflowTest.class.getResourceAsStream(filename);
    final Workflow expected = new WorkflowImpl(BPMN_1_PROCESS_ID, 1, 1, filename);

    // when
    final List<Workflow> workflows =
        client
            .workflowClient()
            .newDeployCommand()
            .addResourceStream(resourceAsStream, filename)
            .send()
            .join()
            .getDeployedWorkflows();

    // then
    assertThat(workflows).containsOnly(expected);
  }

  @Test
  public void shouldDeployWorkflowFromBytes() throws IOException {
    // given
    final String filename = BPMN_1_FILENAME;
    final byte[] bytes = StreamUtil.read(DeployWorkflowTest.class.getResourceAsStream(filename));
    final Workflow expected = new WorkflowImpl(BPMN_1_PROCESS_ID, 1, 1, filename);

    // when
    final List<Workflow> workflows =
        client
            .workflowClient()
            .newDeployCommand()
            .addResourceBytes(bytes, filename)
            .send()
            .join()
            .getDeployedWorkflows();

    // then
    assertThat(workflows).containsOnly(expected);
  }

  @Test
  public void shouldDeployWorkflowFromString() throws IOException {
    // given
    final String filename = BPMN_1_FILENAME;
    final String xml =
        new String(StreamUtil.read(DeployWorkflowTest.class.getResourceAsStream(filename)));
    final Workflow expected = new WorkflowImpl(BPMN_1_PROCESS_ID, 1, 1, filename);

    // when
    final List<Workflow> workflows =
        client
            .workflowClient()
            .newDeployCommand()
            .addResourceString(xml, Charsets.UTF_8, filename)
            .send()
            .join()
            .getDeployedWorkflows();

    // then
    assertThat(workflows).containsOnly(expected);
  }

  @Test
  public void shouldDeployWorkflowFromUtf8String() throws IOException {
    // given
    final String filename = BPMN_1_FILENAME;
    final String xml =
        new String(
            StreamUtil.read(DeployWorkflowTest.class.getResourceAsStream(filename)),
            Charsets.UTF_8);
    final Workflow expected = new WorkflowImpl(BPMN_1_PROCESS_ID, 1, 1, filename);

    // when
    final List<Workflow> workflows =
        client
            .workflowClient()
            .newDeployCommand()
            .addResourceStringUtf8(xml, filename)
            .send()
            .join()
            .getDeployedWorkflows();

    // then
    assertThat(workflows).containsOnly(expected);
  }

  @Test
  public void shouldDeployWorkflowFromWorkflowModel() throws IOException {
    // given
    final String filename = "test.bpmn";
    final BpmnModelInstance workflowModel =
        Bpmn.createExecutableProcess(BPMN_1_PROCESS_ID).startEvent().endEvent().done();
    final Workflow expected = new WorkflowImpl(BPMN_1_PROCESS_ID, 1, 1, filename);

    // when
    final List<Workflow> workflows =
        client
            .workflowClient()
            .newDeployCommand()
            .addWorkflowModel(workflowModel, filename)
            .send()
            .join()
            .getDeployedWorkflows();

    // then
    assertThat(workflows).containsOnly(expected);
  }

  @Test
  public void shouldDeployMultipleWorkflows() throws IOException {
    // given
    final Workflow expected1 =
        new WorkflowImpl(BPMN_1_PROCESS_ID, 1, 1, BPMN_1_FILENAME.substring(1));
    final Workflow expected2 =
        new WorkflowImpl(BPMN_2_PROCESS_ID, 1, 2, BPMN_2_FILENAME.substring(1));
    final Workflow expected3 = new WorkflowImpl(YAML_PROCESS_ID, 1, 3, YAML_FILENAME.substring(1));

    // when
    final List<Workflow> workflows =
        client
            .workflowClient()
            .newDeployCommand()
            .addResourceFromClasspath(expected1.getResourceName())
            .addResourceFromClasspath(expected2.getResourceName())
            .addResourceFromClasspath(expected3.getResourceName())
            .send()
            .join()
            .getDeployedWorkflows();

    // then
    assertThat(workflows).containsOnly(expected1, expected2, expected3);
  }

  @Test
  public void shouldRedeployWorkflow() throws IOException {
    // given
    final Workflow expected1 =
        new WorkflowImpl(BPMN_1_PROCESS_ID, 2, 2, BPMN_1_FILENAME.substring(1));
    final Workflow expected2 =
        new WorkflowImpl(BPMN_2_PROCESS_ID, 1, 3, BPMN_2_FILENAME.substring(1));

    client
        .workflowClient()
        .newDeployCommand()
        .addResourceFromClasspath(expected1.getResourceName())
        .send()
        .join();

    // when
    final List<Workflow> workflows =
        client
            .workflowClient()
            .newDeployCommand()
            .addResourceFromClasspath(expected1.getResourceName())
            .addResourceFromClasspath(expected2.getResourceName())
            .send()
            .join()
            .getDeployedWorkflows();

    // then
    assertThat(workflows).containsOnly(expected1, expected2);
  }

  @Test
  public void shouldDeployWorkflowWithLongResourceName() {
    // given
    final String filename = BPMN_1_FILENAME;
    final InputStream resourceAsStream = DeployWorkflowTest.class.getResourceAsStream(filename);
    final String resourceName =
        "/home/camunda/workspace/_1365-minimal-broker-client-QXKPMWMW4EI6AV6TS2725Y7ASUWR4OE6H7ZHNBG25CGBF3GXXKKQ/clients/java/target/test-classes/workflows/demo-process.bpmn";
    final Workflow expected = new WorkflowImpl(BPMN_1_PROCESS_ID, 1, 1, resourceName);

    // when
    final List<Workflow> workflows =
        client
            .workflowClient()
            .newDeployCommand()
            .addResourceStream(resourceAsStream, resourceName)
            .send()
            .join()
            .getDeployedWorkflows();

    // then
    assertThat(workflows).containsOnly(expected);
  }
}
