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
package io.camunda.zeebe.client.resource.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.camunda.client.protocol.rest.DeploymentDecisionRequirementsResult;
import io.camunda.client.protocol.rest.DeploymentDecisionResult;
import io.camunda.client.protocol.rest.DeploymentFormResult;
import io.camunda.client.protocol.rest.DeploymentMetadataResult;
import io.camunda.client.protocol.rest.DeploymentProcessResult;
import io.camunda.client.protocol.rest.DeploymentResult;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.impl.command.StreamUtil;
import io.camunda.zeebe.client.impl.response.DecisionImpl;
import io.camunda.zeebe.client.impl.response.DecisionRequirementsImpl;
import io.camunda.zeebe.client.impl.response.FormImpl;
import io.camunda.zeebe.client.impl.response.ProcessImpl;
import io.camunda.zeebe.client.resource.DeployResourceTest;
import io.camunda.zeebe.client.util.ClientRestTest;
import io.camunda.zeebe.client.util.RestGatewayPaths;
import io.camunda.zeebe.client.util.RestGatewayService;
import io.camunda.zeebe.client.util.assertions.LoggedRequestAssert;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class DeployResourceRestTest extends ClientRestTest {

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
  private static final String FORM_FILENAME_1 = "/form/test-form-1.form";
  private static final String FORM_FILENAME_2 = "/form/test-form-2.form";
  private static final String DEFAULT_TENANT = "<default>";

  @Test
  public void shouldDeployResourceFromFile() {
    // given
    final String path = DeployResourceTest.class.getResource(BPMN_1_FILENAME).getPath();

    // when
    client.newDeployResourceCommand().addResourceFile(path).send().join();

    // then
    LoggedRequestAssert.assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getDeploymentsUrl());
  }

  @Test
  public void shouldDeployRequestFromClasspath() {
    // given
    final String filename = BPMN_1_FILENAME.substring(1);

    // when
    client.newDeployResourceCommand().addResourceFromClasspath(filename).send().join();

    // then
    LoggedRequestAssert.assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getDeploymentsUrl());
  }

  @Test
  public void shouldDeployResourceFromInputStream() {
    // given
    final String filename = BPMN_1_FILENAME;
    final InputStream resourceAsStream = DeployResourceTest.class.getResourceAsStream(filename);

    // when
    client.newDeployResourceCommand().addResourceStream(resourceAsStream, filename).send().join();

    // then
    LoggedRequestAssert.assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getDeploymentsUrl());
  }

  @Test
  public void shouldDeployResourceFromBytes() {
    // given
    final String filename = BPMN_1_FILENAME;
    final byte[] bytes = getBytes(filename);

    // when
    client.newDeployResourceCommand().addResourceBytes(bytes, filename).send().join();

    // then
    LoggedRequestAssert.assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getDeploymentsUrl());
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
    LoggedRequestAssert.assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getDeploymentsUrl());
  }

  @Test
  public void shouldDeployResourceFromUtf8String() {
    // given
    final String filename = BPMN_1_FILENAME;
    final String xml = new String(getBytes(filename), StandardCharsets.UTF_8);

    // when
    client.newDeployResourceCommand().addResourceStringUtf8(xml, filename).send().join();

    // then
    LoggedRequestAssert.assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getDeploymentsUrl());
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
    LoggedRequestAssert.assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getDeploymentsUrl());
  }

  @Test
  public void shouldDeployMultipleResources() {
    // given
    final long key = 345L;
    final String tenantId = "test-tenant";
    final String filename1 = BPMN_1_FILENAME.substring(1);
    final String filename2 = BPMN_2_FILENAME.substring(1);

    // when
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath(filename1)
        .addResourceFromClasspath(filename2)
        .tenantId(tenantId)
        .send()
        .join();

    // then
    LoggedRequestAssert.assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getDeploymentsUrl());
  }

  @Test
  public void shouldDeployProcessAsResource() {
    // given
    final long key = 123L;
    final String tenantId = "test-tenant";
    final String filename = DeployResourceTest.class.getResource(BPMN_1_FILENAME).getPath();
    gatewayService.onDeploymentsRequest(
        new DeploymentResult()
            .deploymentKey(String.valueOf(key))
            .tenantId(tenantId)
            .addDeploymentsItem(
                new DeploymentMetadataResult()
                    .processDefinition(
                        new DeploymentProcessResult()
                            .processDefinitionId(BPMN_1_PROCESS_ID)
                            .processDefinitionVersion(12)
                            .processDefinitionKey("423")
                            .tenantId(tenantId)
                            .resourceName(filename))));

    // when
    final DeploymentEvent response =
        client
            .newDeployResourceCommand()
            .addResourceFile(filename)
            .tenantId(tenantId)
            .send()
            .join();

    // then
    assertThat(response.getKey()).isEqualTo(key);
    assertThat(response.getTenantId()).isEqualTo(tenantId);
    assertThat(response.getProcesses())
        .containsExactly(new ProcessImpl(423, BPMN_1_PROCESS_ID, 12, filename, tenantId));
  }

  @Test
  public void shouldDeployMultipleProcessesAsResources() {
    // given
    final long key = 345L;
    final String tenantId = "test-tenant";
    final String filename1 = BPMN_1_FILENAME.substring(1);
    final String filename2 = BPMN_2_FILENAME.substring(1);
    gatewayService.onDeploymentsRequest(
        new DeploymentResult()
            .deploymentKey(String.valueOf(key))
            .tenantId(tenantId)
            .addDeploymentsItem(
                new DeploymentMetadataResult()
                    .processDefinition(
                        new DeploymentProcessResult()
                            .processDefinitionId(BPMN_1_PROCESS_ID)
                            .processDefinitionVersion(1)
                            .processDefinitionKey("1")
                            .tenantId(tenantId)
                            .resourceName(filename1)))
            .addDeploymentsItem(
                new DeploymentMetadataResult()
                    .processDefinition(
                        new DeploymentProcessResult()
                            .processDefinitionId(BPMN_2_PROCESS_ID)
                            .processDefinitionVersion(1)
                            .processDefinitionKey("2")
                            .tenantId(tenantId)
                            .resourceName(filename2))));

    // when
    final DeploymentEvent response =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath(filename1)
            .addResourceFromClasspath(filename2)
            .tenantId(tenantId)
            .send()
            .join();

    // then
    assertThat(response.getKey()).isEqualTo(key);
    assertThat(response.getProcesses())
        .containsExactly(
            new ProcessImpl(1, BPMN_1_PROCESS_ID, 1, filename1, tenantId),
            new ProcessImpl(2, BPMN_2_PROCESS_ID, 1, filename2, tenantId));
  }

  @Test
  public void shouldDeployDecisionModelAsResource() {
    // given
    final String filename = DeployResourceTest.class.getResource(DMN_FILENAME).getPath();
    final long deploymentKey = 123L;
    final int version = 1;
    final String tenantId = "test-tenant";
    final long decisionRequirementsKey = 234L;
    final long decisionKey1 = 345L;
    final long decisionKey2 = 456L;
    gatewayService.onDeploymentsRequest(
        new DeploymentResult()
            .deploymentKey(String.valueOf(deploymentKey))
            .tenantId(tenantId)
            .addDeploymentsItem(
                new DeploymentMetadataResult()
                    .decisionDefinition(
                        new DeploymentDecisionResult()
                            .decisionDefinitionId(DMN_DECISION_ID_1)
                            .name(DMN_DECISION_NAME_1)
                            .version(version)
                            .decisionDefinitionKey(String.valueOf(decisionKey1))
                            .decisionRequirementsId(DMN_DECISION_REQUIREMENTS_ID)
                            .decisionRequirementsKey(String.valueOf(decisionRequirementsKey))
                            .tenantId(tenantId)))
            .addDeploymentsItem(
                new DeploymentMetadataResult()
                    .decisionDefinition(
                        new DeploymentDecisionResult()
                            .decisionDefinitionId(DMN_DECISION_ID_2)
                            .name(DMN_DECISION_NAME_2)
                            .version(version)
                            .decisionDefinitionKey(String.valueOf(decisionKey2))
                            .decisionRequirementsId(DMN_DECISION_REQUIREMENTS_ID)
                            .decisionRequirementsKey(String.valueOf(decisionRequirementsKey))
                            .tenantId(tenantId)))
            .addDeploymentsItem(
                new DeploymentMetadataResult()
                    .decisionRequirements(
                        new DeploymentDecisionRequirementsResult()
                            .decisionRequirementsId(DMN_DECISION_REQUIREMENTS_ID)
                            .decisionRequirementsName(DMN_DECISION_REQUIREMENTS_NAME)
                            .version(version)
                            .decisionRequirementsKey(String.valueOf(decisionRequirementsKey))
                            .resourceName(filename)
                            .tenantId(tenantId))));

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
                filename,
                tenantId));
    assertThat(response.getDecisions())
        .containsExactly(
            new DecisionImpl(
                DMN_DECISION_ID_1,
                DMN_DECISION_NAME_1,
                version,
                decisionKey1,
                DMN_DECISION_REQUIREMENTS_ID,
                decisionRequirementsKey,
                tenantId),
            new DecisionImpl(
                DMN_DECISION_ID_2,
                DMN_DECISION_NAME_2,
                version,
                decisionKey2,
                DMN_DECISION_REQUIREMENTS_ID,
                decisionRequirementsKey,
                tenantId));
  }

  @Test
  public void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getDeploymentsUrl(),
        () -> new ProblemDetail().title("Invalid request").status(400));

    // when
    assertThatThrownBy(
            () ->
                client
                    .newDeployResourceCommand()
                    .addResourceStringUtf8("", "test.bpmn")
                    .send()
                    .join())
        .hasMessageContaining("Invalid request");
  }

  @Test
  public void shouldDeployFormAsResource() {
    // given
    final String filename = DeployResourceTest.class.getResource(FORM_FILENAME_1).getPath();
    final long deploymentKey = 123L;
    final int version = 1;
    final long formKey = 234L;
    final String formId = "formId";
    gatewayService.onDeploymentsRequest(
        new DeploymentResult()
            .deploymentKey(String.valueOf(deploymentKey))
            .tenantId(DEFAULT_TENANT)
            .addDeploymentsItem(
                new DeploymentMetadataResult()
                    .form(
                        new DeploymentFormResult()
                            .formId(formId)
                            .version(version)
                            .formKey(String.valueOf(formKey))
                            .resourceName(filename)
                            .tenantId(DEFAULT_TENANT))));

    // when
    final DeploymentEvent response =
        client.newDeployResourceCommand().addResourceFile(filename).send().join();

    // then
    assertThat(response.getKey()).isEqualTo(deploymentKey);
    assertThat(response.getForm())
        .containsExactly(new FormImpl(formId, version, formKey, filename, DEFAULT_TENANT));
  }

  @Test
  public void shouldDeployMultipleFormsAsResources() {
    // given
    final long key = 345L;
    final String filename1 = FORM_FILENAME_1.substring(1);
    final String filename2 = FORM_FILENAME_2.substring(1);
    final String formId1 = "formId1";
    final String formId2 = "formId2";
    gatewayService.onDeploymentsRequest(
        new DeploymentResult()
            .deploymentKey(String.valueOf(key))
            .tenantId(DEFAULT_TENANT)
            .addDeploymentsItem(
                new DeploymentMetadataResult()
                    .form(
                        new DeploymentFormResult()
                            .formId(formId1)
                            .version(1)
                            .formKey("1")
                            .resourceName(filename1)
                            .tenantId(DEFAULT_TENANT)))
            .addDeploymentsItem(
                new DeploymentMetadataResult()
                    .form(
                        new DeploymentFormResult()
                            .formId(formId2)
                            .version(1)
                            .formKey("2")
                            .resourceName(filename2)
                            .tenantId(DEFAULT_TENANT))));

    // when
    final DeploymentEvent response =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath(filename1)
            .addResourceFromClasspath(filename2)
            .tenantId(DEFAULT_TENANT)
            .send()
            .join();

    // then
    assertThat(response.getKey()).isEqualTo(key);
    assertThat(response.getForm())
        .containsExactly(
            new FormImpl(formId1, 1, 1, filename1, DEFAULT_TENANT),
            new FormImpl(formId2, 1, 2, filename2, DEFAULT_TENANT));
  }

  private byte[] getBytes(final String filename) {
    try {
      return StreamUtil.readInputStream(DeployResourceTest.class.getResourceAsStream(filename));
    } catch (final IOException e) {
      throw new AssertionError("Failed to read bytes of file: " + filename, e);
    }
  }
}
