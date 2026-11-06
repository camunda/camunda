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

import io.camunda.client.api.command.VariableMetadataInput;
import io.camunda.client.api.search.enums.ProtectionMode;
import io.camunda.client.protocol.rest.ProcessInstanceCreationInstruction;
import io.camunda.client.protocol.rest.ProcessInstanceResult;
import io.camunda.client.util.ClientRestTest;
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
