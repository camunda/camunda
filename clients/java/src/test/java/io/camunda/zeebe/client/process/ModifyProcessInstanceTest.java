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

import static io.camunda.zeebe.client.util.JsonUtil.fromJsonAsMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.ActivateInstruction;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.TerminateInstruction;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.VariableInstruction;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import org.junit.Test;

public class ModifyProcessInstanceTest extends ClientTest {

  private static final Long PI_KEY = 1L;
  private static final Long EMPTY_KEY = -1L;
  private static final Long ELEMENT_KEY_A = 2L;
  private static final Long ELEMENT_KEY_B = 3L;
  private static final String EMPTY_ELEMENT_ID = "";
  private static final String ELEMENT_ID_A = "elementId_A";
  private static final String ELEMENT_ID_B = "elementId_B";

  @Test
  public void shouldActivateSingleElement() {
    // when
    client.newModifyProcessInstanceCommand(PI_KEY).activateElement(ELEMENT_ID_A).send().join();

    // then
    final ModifyProcessInstanceRequest request = gatewayService.getLastRequest();
    assertRequest(request, 1, 0);
    final ActivateInstruction activateInstruction = request.getActivateInstructions(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 0);
  }

  @Test
  public void shouldActivateMultipleElements() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .activateElement(ELEMENT_ID_A)
        .and()
        .activateElement(ELEMENT_ID_B)
        .send()
        .join();

    // then
    final ModifyProcessInstanceRequest request = gatewayService.getLastRequest();
    assertRequest(request, 2, 0);
    final ActivateInstruction activateInstructionA = request.getActivateInstructions(0);
    assertActivateInstruction(activateInstructionA, ELEMENT_ID_A, EMPTY_KEY, 0);
    final ActivateInstruction activateInstructionB = request.getActivateInstructions(1);
    assertActivateInstruction(activateInstructionB, ELEMENT_ID_B, EMPTY_KEY, 0);
  }

  @Test
  public void shouldActivateMultipleElementsWithVariables() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .activateElement(ELEMENT_ID_A)
        .withVariables(new VariableDocument())
        .and()
        .activateElement(ELEMENT_ID_B)
        .withVariables(new VariableDocument())
        .send()
        .join();

    // then
    final ModifyProcessInstanceRequest request = gatewayService.getLastRequest();
    assertRequest(request, 2, 0);
    final ActivateInstruction activateInstructionA = request.getActivateInstructions(0);
    assertActivateInstruction(activateInstructionA, ELEMENT_ID_A, EMPTY_KEY, 1);
    final VariableInstruction variableInstructionA =
        activateInstructionA.getVariableInstructions(0);
    assertVariableInstruction(variableInstructionA, EMPTY_ELEMENT_ID);
    final ActivateInstruction activateInstructionB = request.getActivateInstructions(1);
    assertActivateInstruction(activateInstructionB, ELEMENT_ID_B, EMPTY_KEY, 1);
    final VariableInstruction variableInstructionB =
        activateInstructionB.getVariableInstructions(0);
    assertVariableInstruction(variableInstructionB, EMPTY_ELEMENT_ID);
  }

  @Test
  public void shouldTerminateSingleElement() {
    // when
    client.newModifyProcessInstanceCommand(PI_KEY).terminateElement(ELEMENT_KEY_A).send().join();

    // then
    final ModifyProcessInstanceRequest request = gatewayService.getLastRequest();
    assertRequest(request, 0, 1);
    final TerminateInstruction terminateInstruction = request.getTerminateInstructions(0);
    assertTerminateInstruction(terminateInstruction, ELEMENT_KEY_A);
  }

  @Test
  public void shouldTerminateMultipleElements() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .terminateElement(ELEMENT_KEY_A)
        .and()
        .terminateElement(ELEMENT_KEY_B)
        .send()
        .join();

    // then
    final ModifyProcessInstanceRequest request = gatewayService.getLastRequest();
    assertRequest(request, 0, 2);
    final TerminateInstruction terminateInstructionA = request.getTerminateInstructions(0);
    assertTerminateInstruction(terminateInstructionA, ELEMENT_KEY_A);
    final TerminateInstruction terminateInstructionB = request.getTerminateInstructions(1);
    assertTerminateInstruction(terminateInstructionB, ELEMENT_KEY_B);
  }

  @Test
  public void shouldActivateAndTerminateElement() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .activateElement(ELEMENT_ID_A)
        .and()
        .terminateElement(ELEMENT_KEY_A)
        .send()
        .join();

    // then
    final ModifyProcessInstanceRequest request = gatewayService.getLastRequest();
    assertRequest(request, 1, 1);
    final ActivateInstruction activateInstruction = request.getActivateInstructions(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 0);
    final TerminateInstruction terminateInstruction = request.getTerminateInstructions(0);
    assertTerminateInstruction(terminateInstruction, ELEMENT_KEY_A);
  }

  @Test
  public void shouldActivateElementWithAncestor() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .activateElement(ELEMENT_ID_A, ELEMENT_KEY_A)
        .send()
        .join();

    // then
    final ModifyProcessInstanceRequest request = gatewayService.getLastRequest();
    assertRequest(request, 1, 0);
    final ActivateInstruction activateInstruction = request.getActivateInstructions(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, ELEMENT_KEY_A, 0);
  }

  @Test
  public void shouldActivateElementWithStringVariables() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .activateElement(ELEMENT_ID_A)
        .withVariables("{\"foo\": \"bar\"}")
        .send()
        .join();

    // then
    final ModifyProcessInstanceRequest request = gatewayService.getLastRequest();
    assertRequest(request, 1, 0);
    final ActivateInstruction activateInstruction = request.getActivateInstructions(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 1);
    final VariableInstruction variableInstruction = activateInstruction.getVariableInstructions(0);
    assertVariableInstruction(variableInstruction, EMPTY_ELEMENT_ID);
  }

  @Test
  public void shouldActivateElementWithInputStreamVariables() {
    // given
    final String variables = "{\"foo\": \"bar\"}";
    final InputStream inputStream =
        new ByteArrayInputStream(variables.getBytes(StandardCharsets.UTF_8));

    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .activateElement(ELEMENT_ID_A)
        .withVariables(inputStream)
        .send()
        .join();

    // then
    final ModifyProcessInstanceRequest request = gatewayService.getLastRequest();
    assertRequest(request, 1, 0);
    final ActivateInstruction activateInstruction = request.getActivateInstructions(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 1);
    final VariableInstruction variableInstruction = activateInstruction.getVariableInstructions(0);
    assertVariableInstruction(variableInstruction, EMPTY_ELEMENT_ID);
  }

  @Test
  public void shouldActivateElementWithMapVariables() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .activateElement(ELEMENT_ID_A)
        .withVariables(Collections.singletonMap("foo", "bar"))
        .send()
        .join();

    // then
    final ModifyProcessInstanceRequest request = gatewayService.getLastRequest();
    assertRequest(request, 1, 0);
    final ActivateInstruction activateInstruction = request.getActivateInstructions(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 1);
    final VariableInstruction variableInstruction = activateInstruction.getVariableInstructions(0);
    assertVariableInstruction(variableInstruction, EMPTY_ELEMENT_ID);
  }

  @Test
  public void shouldActivateElementWithObjectVariables() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .activateElement(ELEMENT_ID_A)
        .withVariables(new VariableDocument())
        .send()
        .join();

    // then
    final ModifyProcessInstanceRequest request = gatewayService.getLastRequest();
    assertRequest(request, 1, 0);
    final ActivateInstruction activateInstruction = request.getActivateInstructions(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 1);
    final VariableInstruction variableInstruction = activateInstruction.getVariableInstructions(0);
    assertVariableInstruction(variableInstruction, EMPTY_ELEMENT_ID);
  }

  @Test
  public void shouldActivateElementWithStringVariablesAndScopeId() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .activateElement(ELEMENT_ID_A)
        .withVariables("{\"foo\": \"bar\"}", ELEMENT_ID_B)
        .send()
        .join();

    // then
    final ModifyProcessInstanceRequest request = gatewayService.getLastRequest();
    assertRequest(request, 1, 0);
    final ActivateInstruction activateInstruction = request.getActivateInstructions(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 1);
    final VariableInstruction variableInstruction = activateInstruction.getVariableInstructions(0);
    assertVariableInstruction(variableInstruction, ELEMENT_ID_B);
  }

  @Test
  public void shouldActivateElementWithInputStreamVariablesAndScopeId() {
    // given
    final String variables = "{\"foo\": \"bar\"}";
    final InputStream inputStream =
        new ByteArrayInputStream(variables.getBytes(StandardCharsets.UTF_8));

    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .activateElement(ELEMENT_ID_A)
        .withVariables(inputStream, ELEMENT_ID_B)
        .send()
        .join();

    // then
    final ModifyProcessInstanceRequest request = gatewayService.getLastRequest();
    assertRequest(request, 1, 0);
    final ActivateInstruction activateInstruction = request.getActivateInstructions(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 1);
    final VariableInstruction variableInstruction = activateInstruction.getVariableInstructions(0);
    assertVariableInstruction(variableInstruction, ELEMENT_ID_B);
  }

  @Test
  public void shouldActivateElementWithMapVariablesAndScopeId() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .activateElement(ELEMENT_ID_A)
        .withVariables(Collections.singletonMap("foo", "bar"), ELEMENT_ID_B)
        .send()
        .join();

    // then
    final ModifyProcessInstanceRequest request = gatewayService.getLastRequest();
    assertRequest(request, 1, 0);
    final ActivateInstruction activateInstruction = request.getActivateInstructions(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 1);
    final VariableInstruction variableInstruction = activateInstruction.getVariableInstructions(0);
    assertVariableInstruction(variableInstruction, ELEMENT_ID_B);
  }

  @Test
  public void shouldActivateElementWithObjectVariablesAndScopeId() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .activateElement(ELEMENT_ID_A)
        .withVariables(new VariableDocument(), ELEMENT_ID_B)
        .send()
        .join();

    // then
    final ModifyProcessInstanceRequest request = gatewayService.getLastRequest();
    assertRequest(request, 1, 0);
    final ActivateInstruction activateInstruction = request.getActivateInstructions(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 1);
    final VariableInstruction variableInstruction = activateInstruction.getVariableInstructions(0);
    assertVariableInstruction(variableInstruction, ELEMENT_ID_B);
  }

  @Test
  public void shouldActivateElementWithMultipleVariables() {
    // given
    final String variables = "{\"foo\": \"bar\"}";
    final InputStream inputStream =
        new ByteArrayInputStream(variables.getBytes(StandardCharsets.UTF_8));

    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .activateElement(ELEMENT_ID_A)
        .withVariables(variables)
        .withVariables(inputStream)
        .withVariables(Collections.singletonMap("foo", "bar"))
        .withVariables(new VariableDocument())
        .send()
        .join();

    // then
    final ModifyProcessInstanceRequest request = gatewayService.getLastRequest();
    assertRequest(request, 1, 0);
    final ActivateInstruction activateInstruction = request.getActivateInstructions(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 4);
    final VariableInstruction variableInstruction1 = activateInstruction.getVariableInstructions(0);
    assertVariableInstruction(variableInstruction1, EMPTY_ELEMENT_ID);
    final VariableInstruction variableInstruction2 = activateInstruction.getVariableInstructions(1);
    assertVariableInstruction(variableInstruction2, EMPTY_ELEMENT_ID);
    final VariableInstruction variableInstruction3 = activateInstruction.getVariableInstructions(2);
    assertVariableInstruction(variableInstruction3, EMPTY_ELEMENT_ID);
    final VariableInstruction variableInstruction4 = activateInstruction.getVariableInstructions(3);
    assertVariableInstruction(variableInstruction4, EMPTY_ELEMENT_ID);
  }

  private void assertRequest(
      final ModifyProcessInstanceRequest request,
      final int expectedStartInstructions,
      final int expectedTerminateInstructions) {
    assertThat(request.getProcessInstanceKey()).isEqualTo(PI_KEY);
    assertThat(request.getActivateInstructionsCount()).isEqualTo(expectedStartInstructions);
    assertThat(request.getTerminateInstructionsCount()).isEqualTo(expectedTerminateInstructions);
  }

  private void assertActivateInstruction(
      final ActivateInstruction activateInstruction,
      final String expectedElementId,
      final long expectedAncestorKey,
      final int expectedVariableInstructions) {
    assertThat(activateInstruction.getElementId()).isEqualTo(expectedElementId);
    assertThat(activateInstruction.getAncestorElementInstanceKey()).isEqualTo(expectedAncestorKey);
    assertThat(activateInstruction.getVariableInstructionsCount())
        .isEqualTo(expectedVariableInstructions);
  }

  private void assertTerminateInstruction(
      final TerminateInstruction terminateInstruction, final long expectedElementKey) {
    assertThat(terminateInstruction.getElementInstanceKey()).isEqualTo(expectedElementKey);
  }

  private void assertVariableInstruction(
      final VariableInstruction variableInstruction, final String expectedScopeId) {
    assertThat(fromJsonAsMap(variableInstruction.getVariables())).containsOnly(entry("foo", "bar"));
    assertThat(variableInstruction.getScopeId()).isEqualTo(expectedScopeId);
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .activateElement(ELEMENT_ID_A)
        .requestTimeout(requestTimeout)
        .send()
        .join();

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }

  private static class VariableDocument {

    private final String foo = "bar";

    VariableDocument() {}

    public String getFoo() {
      return foo;
    }
  }
}
