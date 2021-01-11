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

import static io.zeebe.client.util.RecordingGatewayService.deployedWorkflow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.base.Charsets;
import io.zeebe.client.api.command.ClientException;
import io.zeebe.client.api.response.DeploymentEvent;
import io.zeebe.client.api.response.Workflow;
import io.zeebe.client.impl.command.StreamUtil;
import io.zeebe.client.impl.response.WorkflowImpl;
import io.zeebe.client.util.ClientTest;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployWorkflowRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.WorkflowRequestObject;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import org.junit.Test;

public final class DeployWorkflowTest extends ClientTest {

  public static final String BPMN_1_FILENAME = "/workflows/demo-process.bpmn";
  public static final String BPMN_2_FILENAME = "/workflows/another-demo-process.bpmn";

  public static final String BPMN_1_PROCESS_ID = "demoProcess";
  public static final String BPMN_2_PROCESS_ID = "anotherDemoProcess";

  @Test
  public void shouldDeployWorkflowFromFile() {
    // given
    final long key = 123L;
    final String filename = DeployWorkflowTest.class.getResource(BPMN_1_FILENAME).getPath();
    gatewayService.onDeployWorkflowRequest(
        key, deployedWorkflow(BPMN_1_PROCESS_ID, 12, 423, filename));
    final Workflow expected = new WorkflowImpl(423, BPMN_1_PROCESS_ID, 12, filename);

    // when
    final DeploymentEvent response =
        client.newDeployCommand().addResourceFile(filename).send().join();

    // then
    assertThat(response.getKey()).isEqualTo(key);

    final List<Workflow> workflows = response.getWorkflows();
    assertThat(workflows).containsOnly(expected);

    final DeployWorkflowRequest request = gatewayService.getLastRequest();
    final WorkflowRequestObject workflow = request.getWorkflows(0);
    assertThat(workflow.getName()).isEqualTo(filename);
    assertThat(workflow.getDefinition().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldDeployWorkflowFromClasspath() {
    // given
    final String filename = BPMN_1_FILENAME.substring(1);

    // when
    client.newDeployCommand().addResourceFromClasspath(filename).send().join();

    // then
    final DeployWorkflowRequest request = gatewayService.getLastRequest();
    final WorkflowRequestObject workflow = request.getWorkflows(0);
    assertThat(workflow.getName()).isEqualTo(filename);
    assertThat(workflow.getDefinition().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));
  }

  @Test
  public void shouldDeployWorkflowFromInputStream() {
    // given
    final String filename = BPMN_1_FILENAME;
    final InputStream resourceAsStream = DeployWorkflowTest.class.getResourceAsStream(filename);

    // when
    client.newDeployCommand().addResourceStream(resourceAsStream, filename).send().join();

    // then
    final DeployWorkflowRequest request = gatewayService.getLastRequest();
    final WorkflowRequestObject workflow = request.getWorkflows(0);
    assertThat(workflow.getName()).isEqualTo(filename);
    assertThat(workflow.getDefinition().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));
  }

  @Test
  public void shouldDeployWorkflowFromBytes() {
    // given
    final String filename = BPMN_1_FILENAME;
    final byte[] bytes = getBytes(filename);

    // when
    client.newDeployCommand().addResourceBytes(bytes, filename).send().join();

    // then
    final DeployWorkflowRequest request = gatewayService.getLastRequest();
    final WorkflowRequestObject workflow = request.getWorkflows(0);

    assertThat(workflow.getName()).isEqualTo(filename);
    assertThat(workflow.getDefinition().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));
  }

  @Test
  public void shouldDeployWorkflowFromString() {
    // given
    final String filename = BPMN_1_FILENAME;
    final String xml = new String(getBytes(filename));

    // when
    client.newDeployCommand().addResourceString(xml, Charsets.UTF_8, filename).send().join();

    // then
    final DeployWorkflowRequest request = gatewayService.getLastRequest();
    final WorkflowRequestObject workflow = request.getWorkflows(0);

    assertThat(workflow.getName()).isEqualTo(filename);
    assertThat(workflow.getDefinition().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));
  }

  @Test
  public void shouldDeployWorkflowFromUtf8String() {
    // given
    final String filename = BPMN_1_FILENAME;
    final String xml = new String(getBytes(filename), Charsets.UTF_8);

    // when
    client.newDeployCommand().addResourceStringUtf8(xml, filename).send().join();

    // then
    final DeployWorkflowRequest request = gatewayService.getLastRequest();
    final WorkflowRequestObject workflow = request.getWorkflows(0);

    assertThat(workflow.getName()).isEqualTo(filename);
    assertThat(workflow.getDefinition().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));
  }

  @Test
  public void shouldDeployWorkflowFromWorkflowModel() {
    // given
    final String filename = "test.bpmn";
    final BpmnModelInstance workflowModel =
        Bpmn.createExecutableProcess(BPMN_1_PROCESS_ID).startEvent().endEvent().done();

    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(outStream, workflowModel);
    final byte[] expectedBytes = outStream.toByteArray();

    // when
    client.newDeployCommand().addWorkflowModel(workflowModel, filename).send().join();

    // then
    final DeployWorkflowRequest request = gatewayService.getLastRequest();
    final WorkflowRequestObject workflow = request.getWorkflows(0);

    assertThat(workflow.getName()).isEqualTo(filename);
    assertThat(workflow.getDefinition().toByteArray()).isEqualTo(expectedBytes);
  }

  @Test
  public void shouldDeployMultipleWorkflows() {
    // given
    final long key = 345L;

    final String filename1 = BPMN_1_FILENAME.substring(1);
    final String filename2 = BPMN_2_FILENAME.substring(1);

    final Workflow expected1 = new WorkflowImpl(1, BPMN_1_PROCESS_ID, 1, filename1);
    final Workflow expected2 = new WorkflowImpl(2, BPMN_2_PROCESS_ID, 1, filename2);

    gatewayService.onDeployWorkflowRequest(
        key,
        deployedWorkflow(BPMN_1_PROCESS_ID, 1, 1, filename1),
        deployedWorkflow(BPMN_2_PROCESS_ID, 1, 2, filename2));

    // when
    final DeploymentEvent response =
        client
            .newDeployCommand()
            .addResourceFromClasspath(filename1)
            .addResourceFromClasspath(filename2)
            .send()
            .join();

    // then
    assertThat(response.getKey()).isEqualTo(key);

    final List<Workflow> workflows = response.getWorkflows();
    assertThat(workflows).containsOnly(expected1, expected2);

    final DeployWorkflowRequest request = gatewayService.getLastRequest();
    assertThat(request.getWorkflowsList()).hasSize(2);

    WorkflowRequestObject workflow = request.getWorkflows(0);

    assertThat(workflow.getName()).isEqualTo(filename1);
    assertThat(workflow.getDefinition().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));

    workflow = request.getWorkflows(1);
    assertThat(workflow.getName()).isEqualTo(filename2);
    assertThat(workflow.getDefinition().toByteArray()).isEqualTo(getBytes(BPMN_2_FILENAME));
  }

  @Test
  public void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        DeployWorkflowRequest.class, () -> new ClientException("Invalid request"));

    // when
    assertThatThrownBy(
            () -> client.newDeployCommand().addResourceStringUtf8("", "test.bpmn").send().join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Invalid request");
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client
        .newDeployCommand()
        .addResourceStringUtf8("", "test.bpmn")
        .requestTimeout(requestTimeout)
        .send()
        .join();

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }

  private byte[] getBytes(final String filename) {
    try {
      return StreamUtil.readInputStream(DeployWorkflowTest.class.getResourceAsStream(filename));
    } catch (final IOException e) {
      throw new AssertionError("Failed to read bytes of file: " + filename, e);
    }
  }
}
