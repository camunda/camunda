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
package io.camunda.client.process;

import static io.camunda.client.api.command.CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;
import static io.camunda.client.util.RecordingGatewayService.deployedProcess;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.impl.command.StreamUtil;
import io.camunda.client.impl.response.ProcessImpl;
import io.camunda.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployProcessRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ProcessRequestObject;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.Test;

public final class DeployProcessTest extends ClientTest {

  public static final String BPMN_1_FILENAME = "/processes/demo-process.bpmn";
  public static final String BPMN_2_FILENAME = "/processes/another-demo-process.bpmn";
  public static final String BPMN_1_PROCESS_ID = "demoProcess";
  public static final String BPMN_2_PROCESS_ID = "anotherDemoProcess";

  @Test
  public void shouldDeployProcessFromFile() {
    // given
    final String path = DeployProcessTest.class.getResource(BPMN_1_FILENAME).getPath();

    // when
    client.newDeployCommand().addResourceFile(path).send().join();

    // then
    final DeployProcessRequest request = gatewayService.getLastRequest();
    final ProcessRequestObject process = request.getProcesses(0);
    assertThat(process.getName()).isEqualTo(path);
    assertThat(process.getDefinition().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));
  }

  @Test
  public void shouldDeployRequestFromClasspath() {
    // given
    final String filename = BPMN_1_FILENAME.substring(1);

    // when
    client.newDeployCommand().addResourceFromClasspath(filename).send().join();

    // then
    final DeployProcessRequest request = gatewayService.getLastRequest();
    final ProcessRequestObject process = request.getProcesses(0);
    assertThat(process.getName()).isEqualTo(filename);
    assertThat(process.getDefinition().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));
  }

  @Test
  public void shouldDeployProcessFromInputStream() {
    // given
    final String filename = BPMN_1_FILENAME;
    final InputStream resourceAsStream = DeployProcessTest.class.getResourceAsStream(filename);

    // when
    client.newDeployCommand().addResourceStream(resourceAsStream, filename).send().join();

    // then
    final DeployProcessRequest request = gatewayService.getLastRequest();
    final ProcessRequestObject process = request.getProcesses(0);
    assertThat(process.getName()).isEqualTo(filename);
    assertThat(process.getDefinition().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));
  }

  @Test
  public void shouldDeployProcessFromBytes() {
    // given
    final String filename = BPMN_1_FILENAME;
    final byte[] bytes = getBytes(filename);

    // when
    client.newDeployCommand().addResourceBytes(bytes, filename).send().join();

    // then
    final DeployProcessRequest request = gatewayService.getLastRequest();
    final ProcessRequestObject process = request.getProcesses(0);
    assertThat(process.getName()).isEqualTo(filename);
    assertThat(process.getDefinition().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));
  }

  @Test
  public void shouldDeployProcessFromString() {
    // given
    final String filename = BPMN_1_FILENAME;
    final String xml = new String(getBytes(filename));

    // when
    client
        .newDeployCommand()
        .addResourceString(xml, StandardCharsets.UTF_8, filename)
        .send()
        .join();

    // then
    final DeployProcessRequest request = gatewayService.getLastRequest();
    final ProcessRequestObject process = request.getProcesses(0);
    assertThat(process.getName()).isEqualTo(filename);
    assertThat(process.getDefinition().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));
  }

  @Test
  public void shouldDeployProcessFromUtf8String() {
    // given
    final String filename = BPMN_1_FILENAME;
    final String xml = new String(getBytes(filename), StandardCharsets.UTF_8);

    // when
    client.newDeployCommand().addResourceStringUtf8(xml, filename).send().join();

    // then
    final DeployProcessRequest request = gatewayService.getLastRequest();
    final ProcessRequestObject process = request.getProcesses(0);
    assertThat(process.getName()).isEqualTo(filename);
    assertThat(process.getDefinition().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));
  }

  @Test
  public void shouldDeployProcessFromProcessModel() {
    // given
    final String filename = "test.bpmn";
    final BpmnModelInstance processModel =
        Bpmn.createExecutableProcess(BPMN_1_PROCESS_ID).startEvent().endEvent().done();

    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(outStream, processModel);
    final byte[] expectedBytes = outStream.toByteArray();

    // when
    client.newDeployCommand().addProcessModel(processModel, filename).send().join();

    // then
    final DeployProcessRequest request = gatewayService.getLastRequest();
    final ProcessRequestObject process = request.getProcesses(0);
    assertThat(process.getName()).isEqualTo(filename);
    assertThat(process.getDefinition().toByteArray()).isEqualTo(expectedBytes);
  }

  @Test
  public void shouldDeployMultipleProcesses() {
    // given
    final long key = 345L;
    final String filename1 = BPMN_1_FILENAME.substring(1);
    final String filename2 = BPMN_2_FILENAME.substring(1);
    gatewayService.onDeployProcessRequest(
        key,
        deployedProcess(BPMN_1_PROCESS_ID, 1, 1, filename1),
        deployedProcess(BPMN_2_PROCESS_ID, 1, 2, filename2));

    // when
    client
        .newDeployCommand()
        .addResourceFromClasspath(filename1)
        .addResourceFromClasspath(filename2)
        .send()
        .join();

    // then
    final DeployProcessRequest request = gatewayService.getLastRequest();
    assertThat(request.getProcessesList()).hasSize(2);

    final ProcessRequestObject process1 = request.getProcesses(0);
    assertThat(process1.getName()).isEqualTo(filename1);
    assertThat(process1.getDefinition().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));

    final ProcessRequestObject process2 = request.getProcesses(1);
    assertThat(process2.getName()).isEqualTo(filename2);
    assertThat(process2.getDefinition().toByteArray()).isEqualTo(getBytes(BPMN_2_FILENAME));
  }

  @Test
  public void shouldReceiveDeployedProcessMetadataInResponse() {
    // given
    final long key = 123L;
    final String testTenantId = "test-tenant";
    final String filename = DeployProcessTest.class.getResource(BPMN_1_FILENAME).getPath();
    gatewayService.onDeployProcessRequest(
        key, deployedProcess(BPMN_1_PROCESS_ID, 12, 423, filename, testTenantId));

    // when
    final DeploymentEvent response =
        client.newDeployCommand().addResourceFile(filename).send().join();

    // then
    assertThat(response.getKey()).isEqualTo(key);
    assertThat(response.getProcesses())
        .containsExactly(new ProcessImpl(423, BPMN_1_PROCESS_ID, 12, filename, testTenantId));
  }

  @Test
  public void shouldDeployMultipleProcessesAsResources() {
    // given
    final long key = 345L;
    final String filename1 = BPMN_1_FILENAME.substring(1);
    final String filename2 = BPMN_2_FILENAME.substring(1);
    gatewayService.onDeployProcessRequest(
        key,
        deployedProcess(BPMN_1_PROCESS_ID, 1, 1, filename1),
        deployedProcess(BPMN_2_PROCESS_ID, 1, 2, filename2));

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
    assertThat(response.getProcesses())
        .containsExactly(
            new ProcessImpl(1, BPMN_1_PROCESS_ID, 1, filename1, DEFAULT_TENANT_IDENTIFIER),
            new ProcessImpl(2, BPMN_2_PROCESS_ID, 1, filename2, DEFAULT_TENANT_IDENTIFIER));
  }

  @Test
  public void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        DeployProcessRequest.class, () -> new ClientException("Invalid request"));

    // when
    assertThatThrownBy(
            () -> client.newDeployCommand().addResourceStringUtf8("", "test.bpmn").send().join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Invalid request");
  }

  @Test
  public void shouldUseDefaultRequestTimeout() {
    // when
    client.newDeployCommand().addResourceStringUtf8("", "test.bpmn").send().join();

    // then
    rule.verifyDefaultRequestTimeout();
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
      return StreamUtil.readInputStream(DeployProcessTest.class.getResourceAsStream(filename));
    } catch (final IOException e) {
      throw new AssertionError("Failed to read bytes of file: " + filename, e);
    }
  }
}
