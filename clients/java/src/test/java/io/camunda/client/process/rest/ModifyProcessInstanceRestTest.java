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
import static org.assertj.core.api.Assertions.entry;

import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.AncestorScopeInstruction;
import io.camunda.client.protocol.rest.ModifyProcessInstanceVariableInstruction;
import io.camunda.client.protocol.rest.ProcessInstanceModificationActivateInstruction;
import io.camunda.client.protocol.rest.ProcessInstanceModificationInstruction;
import io.camunda.client.protocol.rest.ProcessInstanceModificationMoveInstruction;
import io.camunda.client.protocol.rest.ProcessInstanceModificationTerminateInstruction;
import io.camunda.client.util.ClientRestTest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class ModifyProcessInstanceRestTest extends ClientRestTest {

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
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 1, 0);
    final ProcessInstanceModificationActivateInstruction activateInstruction =
        request.getActivateInstructions().get(0);
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
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 2, 0);
    final ProcessInstanceModificationActivateInstruction activateInstruction =
        request.getActivateInstructions().get(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 0);
    final ProcessInstanceModificationActivateInstruction activateInstructionB =
        request.getActivateInstructions().get(1);
    assertActivateInstruction(activateInstructionB, ELEMENT_ID_B, EMPTY_KEY, 0);
  }

  @Test
  public void shouldActivateMultipleElementsWithVariables() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .activateElement(ELEMENT_ID_A)
        .withVariables(new ModifyProcessInstanceRestTest.VariableDocument())
        .and()
        .activateElement(ELEMENT_ID_B)
        .withVariables(new ModifyProcessInstanceRestTest.VariableDocument())
        .send()
        .join();

    // then
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 2, 0);
    final ProcessInstanceModificationActivateInstruction activateInstruction =
        request.getActivateInstructions().get(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 1);
    final ModifyProcessInstanceVariableInstruction variableInstructionA =
        activateInstruction.getVariableInstructions().get(0);
    assertVariableInstruction(variableInstructionA, EMPTY_ELEMENT_ID);
    final ProcessInstanceModificationActivateInstruction activateInstructionB =
        request.getActivateInstructions().get(1);
    assertActivateInstruction(activateInstructionB, ELEMENT_ID_B, EMPTY_KEY, 1);
    final ModifyProcessInstanceVariableInstruction variableInstructionB =
        activateInstructionB.getVariableInstructions().get(0);
    assertVariableInstruction(variableInstructionB, EMPTY_ELEMENT_ID);
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
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 1, 0);
    final ProcessInstanceModificationActivateInstruction activateInstruction =
        request.getActivateInstructions().get(0);
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
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 1, 0);
    final ProcessInstanceModificationActivateInstruction activateInstruction =
        request.getActivateInstructions().get(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 1);
    final ModifyProcessInstanceVariableInstruction variableInstruction =
        activateInstruction.getVariableInstructions().get(0);
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
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 1, 0);
    final ProcessInstanceModificationActivateInstruction activateInstruction =
        request.getActivateInstructions().get(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 1);
    final ModifyProcessInstanceVariableInstruction variableInstruction =
        activateInstruction.getVariableInstructions().get(0);
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
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 1, 0);
    final ProcessInstanceModificationActivateInstruction activateInstruction =
        request.getActivateInstructions().get(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 1);
    final ModifyProcessInstanceVariableInstruction variableInstruction =
        activateInstruction.getVariableInstructions().get(0);
    assertVariableInstruction(variableInstruction, EMPTY_ELEMENT_ID);
  }

  @Test
  public void shouldActivateElementWithObjectVariables() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .activateElement(ELEMENT_ID_A)
        .withVariables(new ModifyProcessInstanceRestTest.VariableDocument())
        .send()
        .join();

    // then
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 1, 0);
    final ProcessInstanceModificationActivateInstruction activateInstruction =
        request.getActivateInstructions().get(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 1);
    final ModifyProcessInstanceVariableInstruction variableInstruction =
        activateInstruction.getVariableInstructions().get(0);
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
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 1, 0);
    final ProcessInstanceModificationActivateInstruction activateInstruction =
        request.getActivateInstructions().get(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 1);
    final ModifyProcessInstanceVariableInstruction variableInstruction =
        activateInstruction.getVariableInstructions().get(0);
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
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 1, 0);
    final ProcessInstanceModificationActivateInstruction activateInstruction =
        request.getActivateInstructions().get(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 1);
    final ModifyProcessInstanceVariableInstruction variableInstruction =
        activateInstruction.getVariableInstructions().get(0);
    assertVariableInstruction(variableInstruction, ELEMENT_ID_B);
  }

  @Test
  public void shouldActivateElementWithObjectVariablesAndScopeId() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .activateElement(ELEMENT_ID_A)
        .withVariables(new ModifyProcessInstanceRestTest.VariableDocument(), ELEMENT_ID_B)
        .send()
        .join();

    // then
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 1, 0);
    final ProcessInstanceModificationActivateInstruction activateInstruction =
        request.getActivateInstructions().get(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 1);
    final ModifyProcessInstanceVariableInstruction variableInstruction =
        activateInstruction.getVariableInstructions().get(0);
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
        .withVariables(new ModifyProcessInstanceRestTest.VariableDocument())
        .send()
        .join();

    // then
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 1, 0);
    final ProcessInstanceModificationActivateInstruction activateInstruction =
        request.getActivateInstructions().get(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 4);
    final ModifyProcessInstanceVariableInstruction variableInstruction1 =
        activateInstruction.getVariableInstructions().get(0);
    assertVariableInstruction(variableInstruction1, EMPTY_ELEMENT_ID);
    final ModifyProcessInstanceVariableInstruction variableInstruction2 =
        activateInstruction.getVariableInstructions().get(1);
    assertVariableInstruction(variableInstruction2, EMPTY_ELEMENT_ID);
    final ModifyProcessInstanceVariableInstruction variableInstruction3 =
        activateInstruction.getVariableInstructions().get(2);
    assertVariableInstruction(variableInstruction3, EMPTY_ELEMENT_ID);
    final ModifyProcessInstanceVariableInstruction variableInstruction4 =
        activateInstruction.getVariableInstructions().get(3);
    assertVariableInstruction(variableInstruction4, EMPTY_ELEMENT_ID);
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
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 1, 0);
    final ProcessInstanceModificationActivateInstruction activateInstruction =
        request.getActivateInstructions().get(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 1);
    final ModifyProcessInstanceVariableInstruction variableInstruction =
        activateInstruction.getVariableInstructions().get(0);
    assertVariableInstruction(variableInstruction, ELEMENT_ID_B);
  }

  @Test
  public void shouldActivateElementWithSingleVariableAndScopeId() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .activateElement(ELEMENT_ID_A)
        .withVariable("foo", "bar", ELEMENT_ID_B)
        .send()
        .join();

    // then
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 1, 0);
    final ProcessInstanceModificationActivateInstruction activateInstruction =
        request.getActivateInstructions().get(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 1);
    final ModifyProcessInstanceVariableInstruction variableInstruction =
        activateInstruction.getVariableInstructions().get(0);
    assertVariableInstruction(variableInstruction, ELEMENT_ID_B);
  }

  @Test
  public void shouldActivateElementWithSingleVariable() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .activateElement(ELEMENT_ID_A)
        .withVariable("foo", "bar")
        .send()
        .join();

    // then
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 1, 0);
    final ProcessInstanceModificationActivateInstruction activateInstruction =
        request.getActivateInstructions().get(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 1);
    final ModifyProcessInstanceVariableInstruction variableInstruction =
        activateInstruction.getVariableInstructions().get(0);
    assertVariableInstruction(variableInstruction, EMPTY_ELEMENT_ID);
  }

  @Test
  public void shouldThrowErrorWhenTryToActivateElementWithNullVariable() {
    // when
    Assertions.assertThatThrownBy(
            () ->
                client
                    .newModifyProcessInstanceCommand(PI_KEY)
                    .activateElement(ELEMENT_ID_A)
                    .withVariable(null, null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldTerminateSingleElement() {
    // when
    client.newModifyProcessInstanceCommand(PI_KEY).terminateElement(ELEMENT_KEY_A).send().join();

    // then
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 0, 1);
    final ProcessInstanceModificationTerminateInstruction terminateInstruction =
        request.getTerminateInstructions().get(0);
    assertTerminateInstruction(terminateInstruction, ELEMENT_KEY_A);
  }

  @Test
  public void shouldTerminateMultipleElementsByKey() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .terminateElement(ELEMENT_KEY_A)
        .and()
        .terminateElement(ELEMENT_KEY_B)
        .send()
        .join();

    // then
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 0, 2);
    final ProcessInstanceModificationTerminateInstruction terminateInstructionA =
        request.getTerminateInstructions().get(0);
    assertTerminateInstruction(terminateInstructionA, ELEMENT_KEY_A);
    final ProcessInstanceModificationTerminateInstruction terminateInstructionB =
        request.getTerminateInstructions().get(1);
    assertTerminateInstruction(terminateInstructionB, ELEMENT_KEY_B);
  }

  @Test
  public void shouldTerminateMultipleElementsById() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .terminateElements(ELEMENT_ID_A)
        .and()
        .terminateElements(ELEMENT_ID_B)
        .send()
        .join();

    // then
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 0, 2);
    final ProcessInstanceModificationTerminateInstruction terminateInstructionA =
        request.getTerminateInstructions().get(0);
    assertTerminateInstruction(terminateInstructionA, ELEMENT_ID_A);
    final ProcessInstanceModificationTerminateInstruction terminateInstructionB =
        request.getTerminateInstructions().get(1);
    assertTerminateInstruction(terminateInstructionB, ELEMENT_ID_B);
  }

  @Test
  public void shouldMoveElements() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .moveElements(ELEMENT_ID_A, ELEMENT_ID_B)
        .send()
        .join();

    // then
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 0, 1, 0);
    final ProcessInstanceModificationMoveInstruction moveInstruction =
        request.getMoveInstructions().get(0);
    assertMoveInstruction(moveInstruction, ELEMENT_ID_A, ELEMENT_ID_B, EMPTY_KEY, false, 0);
  }

  @Test
  public void shouldMoveElementsWithAncestorDirect() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .moveElements(ELEMENT_ID_A, ELEMENT_ID_B, ELEMENT_KEY_A)
        .send()
        .join();

    // then
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 0, 1, 0);
    final ProcessInstanceModificationMoveInstruction moveInstruction =
        request.getMoveInstructions().get(0);
    assertMoveInstruction(moveInstruction, ELEMENT_ID_A, ELEMENT_ID_B, ELEMENT_KEY_A, false, 0);
  }

  @Test
  public void shouldMoveElementsWithAncestorSourceParent() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .moveElementsWithInferredAncestor(ELEMENT_ID_A, ELEMENT_ID_B)
        .send()
        .join();

    // then
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 0, 1, 0);
    final ProcessInstanceModificationMoveInstruction moveInstruction =
        request.getMoveInstructions().get(0);
    assertMoveInstruction(moveInstruction, ELEMENT_ID_A, ELEMENT_ID_B, 0, true, 0);
  }

  @Test
  public void shouldMoveElementsWithSingleVariable() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .moveElements(ELEMENT_ID_A, ELEMENT_ID_B)
        .withVariable("foo", "bar")
        .send()
        .join();

    // then
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 0, 1, 0);
    final ProcessInstanceModificationMoveInstruction moveInstruction =
        request.getMoveInstructions().get(0);
    assertMoveInstruction(moveInstruction, ELEMENT_ID_A, ELEMENT_ID_B, EMPTY_KEY, false, 1);
    final ModifyProcessInstanceVariableInstruction variableInstruction =
        moveInstruction.getVariableInstructions().get(0);
    assertVariableInstruction(variableInstruction, EMPTY_ELEMENT_ID);
  }

  @Test
  public void shouldThrowErrorWhenTryToMoveElementWithNullVariable() {
    // when
    Assertions.assertThatThrownBy(
            () ->
                client
                    .newModifyProcessInstanceCommand(PI_KEY)
                    .moveElements(ELEMENT_ID_A, ELEMENT_ID_B)
                    .withVariable(null, null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldActivateAndTerminateAndMoveElements() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .activateElement(ELEMENT_ID_A)
        .and()
        .terminateElement(ELEMENT_KEY_A)
        .and()
        .terminateElements(ELEMENT_ID_A)
        .and()
        .moveElements(ELEMENT_ID_A, ELEMENT_ID_B)
        .send()
        .join();

    // then
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 1, 1, 2);
    final ProcessInstanceModificationActivateInstruction activateInstruction =
        request.getActivateInstructions().get(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 0);
    final ProcessInstanceModificationMoveInstruction moveInstruction =
        request.getMoveInstructions().get(0);
    assertMoveInstruction(moveInstruction, ELEMENT_ID_A, ELEMENT_ID_B, EMPTY_KEY, false, 0);
    final ProcessInstanceModificationTerminateInstruction terminateInstructionKey =
        request.getTerminateInstructions().get(0);
    assertTerminateInstruction(terminateInstructionKey, ELEMENT_KEY_A);
    final ProcessInstanceModificationTerminateInstruction terminateInstructionId =
        request.getTerminateInstructions().get(1);
    assertTerminateInstruction(terminateInstructionId, ELEMENT_ID_A);
  }

  @Test
  public void shouldAssignVariablesToMoveAndActivate() {
    // when
    client
        .newModifyProcessInstanceCommand(PI_KEY)
        .moveElements(ELEMENT_ID_A, ELEMENT_ID_B)
        .withVariable("foo", "bar")
        .and()
        .activateElement(ELEMENT_ID_A)
        .withVariable("foo", "baz")
        .send()
        .join();

    // then
    final ProcessInstanceModificationInstruction request =
        gatewayService.getLastRequest(ProcessInstanceModificationInstruction.class);
    assertRequest(request, 1, 1, 0);
    final ProcessInstanceModificationActivateInstruction activateInstruction =
        request.getActivateInstructions().get(0);
    assertActivateInstruction(activateInstruction, ELEMENT_ID_A, EMPTY_KEY, 1);
    final ModifyProcessInstanceVariableInstruction variableInstruction =
        activateInstruction.getVariableInstructions().get(0);
    assertVariableInstruction(variableInstruction, EMPTY_ELEMENT_ID, "baz");
    final ProcessInstanceModificationMoveInstruction moveInstruction =
        request.getMoveInstructions().get(0);
    assertMoveInstruction(moveInstruction, ELEMENT_ID_A, ELEMENT_ID_B, EMPTY_KEY, false, 1);
    final ModifyProcessInstanceVariableInstruction variableInstructionMove =
        moveInstruction.getVariableInstructions().get(0);
    assertVariableInstruction(variableInstructionMove, EMPTY_ELEMENT_ID);
  }

  private void assertRequest(
      final ProcessInstanceModificationInstruction request,
      final int expectedStartInstructions,
      final int expectedTerminateInstructions) {
    assertThat(request.getActivateInstructions()).hasSize(expectedStartInstructions);
    assertThat(request.getTerminateInstructions()).hasSize(expectedTerminateInstructions);
  }

  private void assertRequest(
      final ProcessInstanceModificationInstruction request,
      final int expectedStartInstructions,
      final int expectedMoveInstructions,
      final int expectedTerminateInstructions) {
    assertThat(request.getActivateInstructions()).hasSize(expectedStartInstructions);
    assertThat(request.getMoveInstructions()).hasSize(expectedMoveInstructions);
    assertThat(request.getTerminateInstructions()).hasSize(expectedTerminateInstructions);
  }

  private void assertActivateInstruction(
      final ProcessInstanceModificationActivateInstruction activateInstruction,
      final String expectedElementId,
      final long expectedAncestorKey,
      final int expectedVariableInstructions) {
    assertThat(activateInstruction.getElementId()).isEqualTo(expectedElementId);
    assertThat(activateInstruction.getAncestorElementInstanceKey())
        .isEqualTo(String.valueOf(expectedAncestorKey));
    assertThat(activateInstruction.getVariableInstructions()).hasSize(expectedVariableInstructions);
  }

  private void assertMoveInstruction(
      final ProcessInstanceModificationMoveInstruction moveInstruction,
      final String expectedSourceElementId,
      final String expectedTargetElementId,
      final long expectedAncestorKey,
      final boolean expectedUseParentScope,
      final int expectedVariableInstructions) {
    assertThat(moveInstruction.getSourceElementInstruction().getSourceType()).isEqualTo("byId");
    assertThat(moveInstruction.getSourceElementInstruction().getSourceElementId())
        .isEqualTo(expectedSourceElementId);
    assertThat(moveInstruction.getTargetElementId()).isEqualTo(expectedTargetElementId);
    if (expectedUseParentScope) {
      assertThat(moveInstruction.getAncestorScopeInstruction())
          .isNotNull()
          .extracting(
              AncestorScopeInstruction::getAncestorScopeType,
              AncestorScopeInstruction::getAncestorElementInstanceKey)
          .containsExactly("inferred", null);
    } else {
      assertThat(moveInstruction.getAncestorScopeInstruction())
          .isNotNull()
          .extracting(
              AncestorScopeInstruction::getAncestorScopeType,
              AncestorScopeInstruction::getAncestorElementInstanceKey)
          .containsExactly("direct", ParseUtil.keyToString(expectedAncestorKey));
    }
    assertThat(moveInstruction.getVariableInstructions()).hasSize(expectedVariableInstructions);
  }

  private void assertTerminateInstruction(
      final ProcessInstanceModificationTerminateInstruction terminateInstruction,
      final long expectedElementKey) {
    assertThat(terminateInstruction.getElementInstanceKey())
        .isEqualTo(String.valueOf(expectedElementKey));
  }

  private void assertTerminateInstruction(
      final ProcessInstanceModificationTerminateInstruction terminateInstruction,
      final String expectedElementId) {
    assertThat(terminateInstruction.getElementId()).isEqualTo(String.valueOf(expectedElementId));
  }

  private void assertVariableInstruction(
      final ModifyProcessInstanceVariableInstruction variableInstruction,
      final String expectedScopeId) {
    Assertions.assertThat(variableInstruction.getVariables()).containsOnly(entry("foo", "bar"));
    assertThat(variableInstruction.getScopeId()).isEqualTo(expectedScopeId);
  }

  private void assertVariableInstruction(
      final ModifyProcessInstanceVariableInstruction variableInstruction,
      final String expectedScopeId,
      final String expectedVariableValue) {
    Assertions.assertThat(variableInstruction.getVariables())
        .containsOnly(entry("foo", expectedVariableValue));
    assertThat(variableInstruction.getScopeId()).isEqualTo(expectedScopeId);
  }

  private static class VariableDocument {

    private final String foo = "bar";

    VariableDocument() {}

    public String getFoo() {
      return foo;
    }
  }
}
