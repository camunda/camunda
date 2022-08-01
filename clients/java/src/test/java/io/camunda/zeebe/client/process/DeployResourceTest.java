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

import static io.camunda.zeebe.client.util.RecordingGatewayService.deployedDecision;
import static io.camunda.zeebe.client.util.RecordingGatewayService.deployedDecisionRequirements;
import static io.camunda.zeebe.client.util.RecordingGatewayService.deployedProcess;
import static io.camunda.zeebe.client.util.RecordingGatewayService.deployment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.impl.command.StreamUtil;
import io.camunda.zeebe.client.impl.response.DecisionImpl;
import io.camunda.zeebe.client.impl.response.DecisionRequirementsImpl;
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
import org.junit.Test;

public final class DeployResourceTest extends ClientTest {

  public static final String BPMN_1_FILENAME = "/processes/demo-process.bpmn";
  public static final String BPMN_2_FILENAME = "/processes/another-demo-process.bpmn";
  public static final String BPMN_1_PROCESS_ID = "demoProcess";
  public static final String BPMN_2_PROCESS_ID = "anotherDemoProcess";

  private static final String DMN_FILENAME = "/dmn/drg-force-user.dmn";
  private static final String DMN_DECISION_ID_1 = "force_user";
  private static final String DMN_DECISION_NAME_1 = "Which force user?";
  private static final String DMN_DECISION_ID_2 = "jedi_or_sith";
  private static final String DMN_DECISION_NAME_2 = "Jedi or Sith?";
  private static final String DMN_DECISION_REQUIREMENTS_ID = "force_users";
  private static final String DMN_DECISION_REQUIREMENTS_NAME = "Force Users";

  @Test
  public void shouldDeployResourceFromFile() {
    // given
    final String path = DeployResourceTest.class.getResource(BPMN_1_FILENAME).getPath();

    // when
    client.newDeployResourceCommand().addResourceFile(path).send().join();

    // then
    final DeployResourceRequest request = gatewayService.getLastRequest();
    final Resource resource = request.getResources(0);
    assertThat(resource.getName()).isEqualTo(path);
    assertThat(resource.getContent().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));
  }

  @Test
  public void shouldDeployRequestFromClasspath() {
    // given
    final String filename = BPMN_1_FILENAME.substring(1);

    // when
    client.newDeployResourceCommand().addResourceFromClasspath(filename).send().join();

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
    client.newDeployResourceCommand().addResourceStream(resourceAsStream, filename).send().join();

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
    client.newDeployResourceCommand().addResourceBytes(bytes, filename).send().join();

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
        .newDeployResourceCommand()
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
    client.newDeployResourceCommand().addResourceStringUtf8(xml, filename).send().join();

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
    client.newDeployResourceCommand().addProcessModel(processModel, filename).send().join();

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
    gatewayService.onDeployResourceRequest(
        key,
        deployment(deployedProcess(BPMN_1_PROCESS_ID, 1, 1, filename1)),
        deployment(deployedProcess(BPMN_2_PROCESS_ID, 1, 2, filename2)));

    // when
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath(filename1)
        .addResourceFromClasspath(filename2)
        .send()
        .join();

    // then
    final DeployResourceRequest request = gatewayService.getLastRequest();
    assertThat(request.getResourcesList()).hasSize(2);

    final Resource resource1 = request.getResources(0);
    assertThat(resource1.getName()).isEqualTo(filename1);
    assertThat(resource1.getContent().toByteArray()).isEqualTo(getBytes(BPMN_1_FILENAME));

    final Resource resource2 = request.getResources(1);
    assertThat(resource2.getName()).isEqualTo(filename2);
    assertThat(resource2.getContent().toByteArray()).isEqualTo(getBytes(BPMN_2_FILENAME));
  }

  @Test
  public void shouldDeployProcessAsResource() {
    // given
    final long key = 123L;
    final String filename = DeployResourceTest.class.getResource(BPMN_1_FILENAME).getPath();
    gatewayService.onDeployResourceRequest(
        key, deployment(deployedProcess(BPMN_1_PROCESS_ID, 12, 423, filename)));

    // when
    final DeploymentEvent response =
        client.newDeployResourceCommand().addResourceFile(filename).send().join();

    // then
    assertThat(response.getKey()).isEqualTo(key);
    assertThat(response.getProcesses())
        .containsExactly(new ProcessImpl(423, BPMN_1_PROCESS_ID, 12, filename));
  }

  @Test
  public void shouldDeployMultipleProcessesAsResources() {
    // given
    final long key = 345L;
    final String filename1 = BPMN_1_FILENAME.substring(1);
    final String filename2 = BPMN_2_FILENAME.substring(1);
    gatewayService.onDeployResourceRequest(
        key,
        deployment(deployedProcess(BPMN_1_PROCESS_ID, 1, 1, filename1)),
        deployment(deployedProcess(BPMN_2_PROCESS_ID, 1, 2, filename2)));

    // when
    final DeploymentEvent response =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath(filename1)
            .addResourceFromClasspath(filename2)
            .send()
            .join();

    // then
    assertThat(response.getKey()).isEqualTo(key);
    assertThat(response.getProcesses())
        .containsExactly(
            new ProcessImpl(1, BPMN_1_PROCESS_ID, 1, filename1),
            new ProcessImpl(2, BPMN_2_PROCESS_ID, 1, filename2));
  }

  @Test
  public void shouldDeployDecisionModelAsResource() {
    // given
    final String filename = DeployResourceTest.class.getResource(DMN_FILENAME).getPath();
    final long deploymentKey = 123L;
    final int version = 1;
    final long decisionRequirementsKey = 234L;
    final long decisionKey1 = 345L;
    final long decisionKey2 = 456L;
    gatewayService.onDeployResourceRequest(
        deploymentKey,
        deployment(
            deployedDecisionRequirements(
                DMN_DECISION_REQUIREMENTS_ID,
                DMN_DECISION_REQUIREMENTS_NAME,
                version,
                decisionRequirementsKey,
                filename)),
        deployment(
            deployedDecision(
                DMN_DECISION_ID_1,
                DMN_DECISION_NAME_1,
                version,
                decisionKey1,
                DMN_DECISION_REQUIREMENTS_ID,
                decisionRequirementsKey)),
        deployment(
            deployedDecision(
                DMN_DECISION_ID_2,
                DMN_DECISION_NAME_2,
                version,
                decisionKey2,
                DMN_DECISION_REQUIREMENTS_ID,
                decisionRequirementsKey)));

    // when
    final DeploymentEvent response =
        client.newDeployResourceCommand().addResourceFile(filename).send().join();

    // then
    assertThat(response.getKey()).isEqualTo(deploymentKey);
    assertThat(response.getDecisionRequirements())
        .containsExactly(
            new DecisionRequirementsImpl(
                DMN_DECISION_REQUIREMENTS_ID,
                DMN_DECISION_REQUIREMENTS_NAME,
                version,
                decisionRequirementsKey,
                filename));
    assertThat(response.getDecisions())
        .containsExactly(
            new DecisionImpl(
                DMN_DECISION_ID_1,
                DMN_DECISION_NAME_1,
                version,
                decisionKey1,
                DMN_DECISION_REQUIREMENTS_ID,
                decisionRequirementsKey),
            new DecisionImpl(
                DMN_DECISION_ID_2,
                DMN_DECISION_NAME_2,
                version,
                decisionKey2,
                DMN_DECISION_REQUIREMENTS_ID,
                decisionRequirementsKey));
  }

  @Test
  public void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        DeployResourceRequest.class, () -> new ClientException("Invalid request"));

    // when
    assertThatThrownBy(
            () ->
                client
                    .newDeployResourceCommand()
                    .addResourceStringUtf8("", "test.bpmn")
                    .send()
                    .join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Invalid request");
  }

  @Test
  public void shouldUseDefaultRequestTimeout() {
    // when
    client.newDeployResourceCommand().addResourceStringUtf8("", "test.bpmn").send().join();

    // then
    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client
        .newDeployResourceCommand()
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
