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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3;
import io.camunda.client.api.command.VariableMetadataInput;
import io.camunda.client.api.search.enums.ProtectionMode;
import io.camunda.client.protocol.rest.ProcessInstanceCreationInstruction;
import io.camunda.client.protocol.rest.ProcessInstanceResult;
import io.camunda.client.protocol.rest.ProtectionModeEnum;
import io.camunda.client.util.ClientRestTest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class VariablesMetadataCommandRestTest extends ClientRestTest {

  private static final ProcessInstanceResult DUMMY_RESPONSE =
      Instancio.create(ProcessInstanceResult.class)
          .processInstanceKey("1")
          .parentProcessInstanceKey("2")
          .parentElementInstanceKey("3")
          .processDefinitionKey("4");

  @Test
  public void shouldAccumulateMetadataWithAddedVariables() {
    // given
    gatewayService.onCreateProcessInstanceRequest(DUMMY_RESPONSE);

    // when
    client
        .newCreateInstanceCommand()
        .bpmnProcessId("testProcess")
        .latestVersion()
        .addVariable("a", 1, VariableMetadataInput.redacted())
        .addVariable("b", 2, VariableMetadataInput.redacted())
        .addVariable("c", 3)
        .send()
        .join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getVariablesMetadata()).containsOnlyKeys("a", "b");
  }

  @Test
  public void shouldKeepProtectionsDeclaredBeforeForTheSameVariable() {
    // given
    gatewayService.onCreateProcessInstanceRequest(DUMMY_RESPONSE);
    final Map<String, Object> variables = new HashMap<>();
    variables.put("x", 3);
    variables.put("y", 4);

    // when
    client
        .newCreateInstanceCommand()
        .bpmnProcessId("testProcess")
        .latestVersion()
        .addVariable("x", 1, VariableMetadataInput.redacted())
        .addVariable("x", 2, VariableMetadataInput.builder().build())
        .addVariables(variables, VariableMetadataInput.redacted().forVariables("x", "y"))
        .addVariable("y", 5, VariableMetadataInput.of())
        .send()
        .join();

    // then
    // later declarations for a variable add to the earlier ones instead of replacing them
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getVariablesMetadata()).containsOnlyKeys("x", "y");
    assertThat(request.getVariablesMetadata().get("x").getProtectionModes())
        .containsExactly(ProtectionModeEnum.REDACT);
    assertThat(request.getVariablesMetadata().get("y").getProtectionModes())
        .containsExactly(ProtectionModeEnum.REDACT);
  }

  @Test
  public void shouldNotQueueVariablesWhenAddedMetadataIsRejected() {
    // given
    gatewayService.onCreateProcessInstanceRequest(DUMMY_RESPONSE);
    final Map<String, VariableMetadataInput> invalidMetadata = new HashMap<>();
    invalidMetadata.put("password", null);
    final CreateProcessInstanceCommandStep3 command =
        client.newCreateInstanceCommand().bpmnProcessId("testProcess").latestVersion();

    // when
    assertThatThrownBy(
            () -> command.addVariables(Collections.singletonMap("password", "x"), invalidMetadata))
        .isInstanceOf(IllegalArgumentException.class);
    command.addVariable("safe", "ok").send().join();

    // then
    // a rejected call must not leave its variables behind without their metadata
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getVariables()).containsOnlyKeys("safe");
    assertThat(request.getVariablesMetadata()).isNullOrEmpty();
  }

  @Test
  public void shouldKeepAccumulatedStateWhenReplacingMetadataIsRejected() {
    // given
    gatewayService.onCreateProcessInstanceRequest(DUMMY_RESPONSE);
    final Map<String, VariableMetadataInput> invalidMetadata = new HashMap<>();
    invalidMetadata.put("password", null);
    final CreateProcessInstanceCommandStep3 command =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("testProcess")
            .latestVersion()
            .addVariable("secret", "val", VariableMetadataInput.redacted());

    // when
    assertThatThrownBy(
            () -> command.variables(Collections.singletonMap("password", "x"), invalidMetadata))
        .isInstanceOf(IllegalArgumentException.class);
    command.send().join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getVariables()).containsOnlyKeys("secret");
    assertThat(request.getVariablesMetadata()).containsOnlyKeys("secret");
  }

  @Test
  public void shouldDropMetadataWhenVariablesAreReplaced() {
    // given
    gatewayService.onCreateProcessInstanceRequest(DUMMY_RESPONSE);

    // when
    client
        .newCreateInstanceCommand()
        .bpmnProcessId("testProcess")
        .latestVersion()
        .variable("a", 1, VariableMetadataInput.redacted())
        .variable("b", 2)
        .send()
        .join();

    // then
    assertThat(gatewayService.getLastRequest(Map.class)).doesNotContainKey("variablesMetadata");
  }

  @Test
  public void shouldNotSendVariableMetadataThatDeclaresNoProtectionModes() {
    // given
    gatewayService.onCreateProcessInstanceRequest(DUMMY_RESPONSE);

    // when
    client
        .newCreateInstanceCommand()
        .bpmnProcessId("testProcess")
        .latestVersion()
        .addVariable("a", 1, VariableMetadataInput.builder().build())
        .addVariable("b", 2, VariableMetadataInput.redacted())
        .send()
        .join();

    // then
    // an entry without declared protection modes would change the modes the variable already has
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getVariablesMetadata()).containsOnlyKeys("b");
  }

  @Test
  public void shouldApplyMetadataToSeveralVariablesByName() {
    // given
    gatewayService.onCreateProcessInstanceRequest(DUMMY_RESPONSE);

    final Map<String, Object> variables = new HashMap<>();
    variables.put("password", "x");
    variables.put("token", "y");
    variables.put("name", "z");

    // when
    client
        .newCreateInstanceCommand()
        .bpmnProcessId("testProcess")
        .latestVersion()
        .variables(
            variables,
            VariableMetadataInput.of(ProtectionMode.REDACT).forVariables("password", "token"))
        .send()
        .join();

    // then
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getVariablesMetadata()).containsOnlyKeys("password", "token");
  }

  @Test
  public void shouldSendExplicitlyEmptyProtectionModes() {
    // given
    gatewayService.onCreateProcessInstanceRequest(DUMMY_RESPONSE);

    // when
    client
        .newCreateInstanceCommand()
        .bpmnProcessId("testProcess")
        .latestVersion()
        .addVariable("a", 1, VariableMetadataInput.builder().protectionModes().build())
        .send()
        .join();

    // then
    // declaring no protections is explicit, so the variable is meant to lose its protections
    final ProcessInstanceCreationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceCreationInstruction.class);
    assertThat(request.getVariablesMetadata()).containsOnlyKeys("a");
    assertThat(request.getVariablesMetadata().get("a").getProtectionModes()).isEmpty();
  }

  @Test
  public void shouldRejectMetadataForCommandsWithoutSupport() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newCompleteCommand(1L)
                    .variable("secret", "val", VariableMetadataInput.redacted()))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("does not support variables metadata");
  }

  @Test
  public void shouldAcceptVariablesWithoutMetadataForCommandsWithoutSupport() {
    // when / then
    assertThat(client.newCompleteCommand(1L).variable("a", 1)).isNotNull();
  }
}
