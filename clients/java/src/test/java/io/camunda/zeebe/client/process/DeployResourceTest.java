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
package io.camunda.zeebe.client.process;

import static io.camunda.zeebe.client.util.RecordingGatewayService.deployedProcess;
import static io.camunda.zeebe.client.util.RecordingGatewayService.deployedResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.impl.command.StreamUtil;
import io.camunda.zeebe.client.impl.response.ProcessImpl;
import io.camunda.zeebe.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Resource;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.Test;

public final class DeployResourceTest extends ClientTest {

  public static final String BPMN_1_FILENAME = "/processes/demo-process.bpmn";
  public static final String BPMN_2_FILENAME = "/processes/another-demo-process.bpmn";

  public static final String BPMN_1_PROCESS_ID = "demoProcess";
  public static final String BPMN_2_PROCESS_ID = "anotherDemoProcess";

  @Test
  public void shouldDeployResourceFromFile() {
    // given
    final long key = 123L;
    final String filename = DeployResourceTest.class.getResource(BPMN_1_FILENAME).getPath();
    gatewayService.onDeployResourceRequest(
        key, deployedResource(deployedProcess(BPMN_1_PROCESS_ID, 12, 423, filename)));
    final Process expected = new ProcessImpl(423, BPMN_1_PROCESS_ID, 12, filename);

    // when
    final DeploymentEvent response =
        client.newDeployCommand().addResourceFile(filename).send().join();

    // then
    assertThat(response.getKey()).isEqualTo(key);

    final List<Process> processes = response.getProcesses();
    assertThat(processes).containsOnly(expected);

    final DeployResourceRequest request = gatewayService.getLastRequest();
    final Resource resource = request.getResources(0);
    assertThat(resource.getName()).isEqualTo(filename);
    assertThat(resource.getContent().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldDeployRequestFromClasspath() {
    // given
    final String filename = BPMN_1_FILENAME.substring(1);

    // when
    client.newDeployCommand().addResourceFromClasspath(filename).send().join();

    // then
    final DeployResourceRequest request = gatewayService.getLastRequest();
    final Resource resource = request.getResources(0);
    assertThat(resource.getName()).isEqualTo(filename);
    assertThat(resource.getContent().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));
  }

  @Test
  public void shouldDeployResourceFromInputStream() {
    // given
    final String filename = BPMN_1_FILENAME;
    final InputStream resourceAsStream = DeployResourceTest.class.getResourceAsStream(filename);

    // when
    client.newDeployCommand().addResourceStream(resourceAsStream, filename).send().join();

    // then
    final DeployResourceRequest request = gatewayService.getLastRequest();
    final Resource resource = request.getResources(0);
    assertThat(resource.getName()).isEqualTo(filename);
    assertThat(resource.getContent().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));
  }

  @Test
  public void shouldDeployResourceFromBytes() {
    // given
    final String filename = BPMN_1_FILENAME;
    final byte[] bytes = getBytes(filename);

    // when
    client.newDeployCommand().addResourceBytes(bytes, filename).send().join();

    // then
    final DeployResourceRequest request = gatewayService.getLastRequest();
    final Resource resource = request.getResources(0);

    assertThat(resource.getName()).isEqualTo(filename);
    assertThat(resource.getContent().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));
  }

  @Test
  public void shouldDeployResourceFromString() {
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
    final DeployResourceRequest request = gatewayService.getLastRequest();
    final Resource resource = request.getResources(0);

    assertThat(resource.getName()).isEqualTo(filename);
    assertThat(resource.getContent().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));
  }

  @Test
  public void shouldDeployResourceFromUtf8String() {
    // given
    final String filename = BPMN_1_FILENAME;
    final String xml = new String(getBytes(filename), StandardCharsets.UTF_8);

    // when
    client.newDeployCommand().addResourceStringUtf8(xml, filename).send().join();

    // then
    final DeployResourceRequest request = gatewayService.getLastRequest();
    final Resource resource = request.getResources(0);

    assertThat(resource.getName()).isEqualTo(filename);
    assertThat(resource.getContent().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));
  }

  @Test
  public void shouldDeployResourceFromProcessModel() {
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
    final DeployResourceRequest request = gatewayService.getLastRequest();
    final Resource resource = request.getResources(0);

    assertThat(resource.getName()).isEqualTo(filename);
    assertThat(resource.getContent().toByteArray()).isEqualTo(expectedBytes);
  }

  @Test
  public void shouldDeployMultipleResources() {
    // given
    final long key = 345L;

    final String filename1 = BPMN_1_FILENAME.substring(1);
    final String filename2 = BPMN_2_FILENAME.substring(1);

    final Process expected1 = new ProcessImpl(1, BPMN_1_PROCESS_ID, 1, filename1);
    final Process expected2 = new ProcessImpl(2, BPMN_2_PROCESS_ID, 1, filename2);

    gatewayService.onDeployResourceRequest(
        key,
        deployedResource(deployedProcess(BPMN_1_PROCESS_ID, 1, 1, filename1)),
        deployedResource(deployedProcess(BPMN_2_PROCESS_ID, 1, 2, filename2)));

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

    final List<Process> processes = response.getProcesses();
    assertThat(processes).containsOnly(expected1, expected2);

    final DeployResourceRequest request = gatewayService.getLastRequest();
    assertThat(request.getResourcesList()).hasSize(2);

    Resource resource = request.getResources(0);

    assertThat(resource.getName()).isEqualTo(filename1);
    assertThat(resource.getContent().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));

    resource = request.getResources(1);
    assertThat(resource.getName()).isEqualTo(filename2);
    assertThat(resource.getContent().toByteArray()).isEqualTo(getBytes(BPMN_2_FILENAME));
  }

  @Test
  public void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        DeployResourceRequest.class, () -> new ClientException("Invalid request"));

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
      return StreamUtil.readInputStream(DeployResourceTest.class.getResourceAsStream(filename));
    } catch (final IOException e) {
      throw new AssertionError("Failed to read bytes of file: " + filename, e);
    }
  }
}
