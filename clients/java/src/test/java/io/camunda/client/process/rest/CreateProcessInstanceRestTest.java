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
package io.camunda.client.process.rest;

import static io.camunda.client.api.command.CreateProcessInstanceCommandStep1.LATEST_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.client.api.command.CommandWithTenantStep;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.protocol.rest.ProcessInstanceCreationInstruction;
import io.camunda.client.protocol.rest.ProcessInstanceCreationStartInstruction;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class CreateProcessInstanceRestTest extends ClientRestTest {

  public static final String ELEMENT_ID_A = "elementId_A";
  public static final String ELEMENT_ID_B = "elementId_B";

  @Test
  public void shouldCreateProcessInstanceByProcessInstanceKey() {
    // when
    final ProcessInstanceEvent response =
        client.newCreateInstanceCommand().processDefinitionKey(123).send().join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getProcessDefinitionKey()).isEqualTo("123");
  }

  @Test
  public void shouldCreateProcessInstanceByBpmnProcessId() {
    // when
    client.newCreateInstanceCommand().bpmnProcessId("testProcess").latestVersion().send().join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getProcessDefinitionId()).isEqualTo("testProcess");
    assertThat(request.getProcessDefinitionVersion()).isEqualTo(LATEST_VERSION);
  }

  @Test
  public void shouldCreateProcessInstanceByBpmnProcessIdAndVersion() {
    // when
    client.newCreateInstanceCommand().bpmnProcessId("testProcess").version(123).send().join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getProcessDefinitionId()).isEqualTo("testProcess");
    assertThat(request.getProcessDefinitionVersion()).isEqualTo(123);
  }

  @Test
  public void shouldCreateProcessInstanceWithStringVariables() {
    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .variables("{\"foo\": \"bar\"}")
        .send()
        .join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    Assertions.assertThat(request.getVariables()).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldCreateProcessInstanceWithInputStreamVariables() {
    // given
    final String variables = "{\"foo\": \"bar\"}";
    final InputStream inputStream =
        new ByteArrayInputStream(variables.getBytes(StandardCharsets.UTF_8));

    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .variables(inputStream)
        .send()
        .join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    Assertions.assertThat(request.getVariables()).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldCreateProcessInstanceWithSingleVariable() {
    // given
    final String key = "key";
    final String value = "value";

    // when
    client.newCreateInstanceCommand().processDefinitionKey(123).variable(key, value).send().join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    Assertions.assertThat(request.getVariables()).containsOnly(entry(key, value));
  }

  @Test
  public void shouldCreateProcessInstanceWithMapVariables() {
    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .variables(Collections.singletonMap("foo", "bar"))
        .send()
        .join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    Assertions.assertThat(request.getVariables()).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldCreateProcessInstanceWithObjectVariables() {
    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .variables(new CreateProcessInstanceRestTest.VariableDocument())
        .send()
        .join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    Assertions.assertThat(request.getVariables()).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldRaiseAnErrorIfRequestFails() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getCreateProcessInstanceUrl(),
        () -> new ProblemDetail().title("Invalid request").status(400));

    // when
    assertThatThrownBy(
            () -> client.newCreateInstanceCommand().processDefinitionKey(123).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Invalid request");
  }

  @Test
  public void shouldAddStartInstruction() {
    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .startBeforeElement(ELEMENT_ID_A)
        .send()
        .join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);

    final List<ProcessInstanceCreationStartInstruction> startInstructionList =
        request.getStartInstructions();
    assertThat(startInstructionList).hasSize(1);
    final ProcessInstanceCreationStartInstruction startInstructionA = startInstructionList.get(0);
    assertThat(startInstructionA.getElementId()).isEqualTo(ELEMENT_ID_A);
  }

  @Test
  public void shouldAddMultipleStartInstructions() {
    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(123)
        .startBeforeElement(ELEMENT_ID_A)
        .startBeforeElement(ELEMENT_ID_B)
        .send()
        .join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);

    final List<ProcessInstanceCreationStartInstruction> startInstructionList =
        request.getStartInstructions();
    assertThat(startInstructionList).hasSize(2);
    final ProcessInstanceCreationStartInstruction startInstructionA = startInstructionList.get(0);
    assertThat(startInstructionA.getElementId()).isEqualTo(ELEMENT_ID_A);
    final ProcessInstanceCreationStartInstruction startInstructionB = startInstructionList.get(1);
    assertThat(startInstructionB.getElementId()).isEqualTo(ELEMENT_ID_B);
  }

  @Test
  public void shouldUseDefaultTenantId() {
    // when
    client.newCreateInstanceCommand().bpmnProcessId("test").latestVersion().send().join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getTenantId()).isEqualTo(CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldAllowSpecifyingTenantIdByLatestVersionOfProcessId() {
    // given
    final String bpmnProcessId = "testProcess";
    final String tenantId = "test-tenant";

    // when
    client
        .newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .latestVersion()
        .tenantId(tenantId)
        .send()
        .join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  public void shouldAllowSpecifyingTenantIdByProcessIdAndVersion() {
    // given
    final String bpmnProcessId = "testProcess";
    final int version = 3;
    final String tenantId = "test-tenant";

    // when
    client
        .newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .version(version)
        .tenantId(tenantId)
        .send()
        .join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  public void shouldAllowSpecifyingTenantIdByProcessDefinitionKey() {
    // given
    final String customTenantId = "test-tenant";
    final Long processDefinitionKey = 1L;

    // when
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .tenantId(customTenantId)
        .send()
        .join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getTenantId()).isEqualTo(customTenantId);
    assertThat(request.getProcessDefinitionKey()).isEqualTo(String.valueOf(processDefinitionKey));
  }

  public static class VariableDocument {

    VariableDocument() {}

    public String getFoo() {
      return "bar";
    }
  }
}
