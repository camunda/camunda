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
package io.camunda.process.test.impl.dsl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.dsl.ImmutableElementSelector;
import io.camunda.process.test.api.dsl.ImmutableProcessInstanceSelector;
import io.camunda.process.test.api.dsl.instructions.AssertElementInstanceInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableAssertElementInstanceInstruction;
import io.camunda.process.test.api.dsl.instructions.assertElementInstance.ElementInstanceState;
import io.camunda.process.test.impl.dsl.instructions.AssertElementInstanceInstructionHandler;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AssertElementInstanceInstructionTest {

  private static final String PROCESS_DEFINITION_ID = "process";
  private static final String ELEMENT_ID = "task1";
  private static final String ELEMENT_NAME = "Task A";

  @Mock private CamundaProcessTestContext processTestContext;
  @Mock private CamundaClient camundaClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private AssertionFacade assertionFacade;

  @Mock private ProcessInstanceFilter processInstanceFilter;
  @Mock private ElementInstanceFilter elementInstanceFilter;
  @Captor private ArgumentCaptor<ProcessInstanceSelector> processInstanceSelectorCaptor;
  @Captor private ArgumentCaptor<ElementSelector> elementSelectorCaptor;

  private final AssertElementInstanceInstructionHandler instructionHandler =
      new AssertElementInstanceInstructionHandler();

  @Test
  void shouldSelectProcessInstanceByProcessDefinitionId() {
    // given
    final AssertElementInstanceInstruction instruction =
        ImmutableAssertElementInstanceInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
            .state(ElementInstanceState.IS_ACTIVE)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(processInstanceSelectorCaptor.capture());

    processInstanceSelectorCaptor.getValue().applyFilter(processInstanceFilter);
    verify(processInstanceFilter).processDefinitionId(PROCESS_DEFINITION_ID);

    verifyNoMoreInteractions(camundaClient, processTestContext);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("stateAssertionArgumentStream")
  void shouldAssertElementStateWithDefaultAmount(
      final ElementInstanceState state,
      final BiConsumer<ProcessInstanceAssert, Integer> expectedAssertion) {
    // given
    final AssertElementInstanceInstruction instruction =
        ImmutableAssertElementInstanceInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
            .state(state)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(any());

    expectedAssertion.accept(verify(processInstanceAssert), 1);

    verifyNoMoreInteractions(camundaClient, processTestContext);
  }

  @Test
  void shouldAssertElementStateWithCustomAmount() {
    // given
    final int customAmount = 3;
    final AssertElementInstanceInstruction instruction =
        ImmutableAssertElementInstanceInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
            .state(ElementInstanceState.IS_ACTIVE)
            .amount(customAmount)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(any());
    verify(processInstanceAssert).hasActiveElement(any(ElementSelector.class), eq(customAmount));

    verifyNoMoreInteractions(camundaClient, processTestContext);
  }

  @Test
  void shouldSelectElementById() {
    // given
    final AssertElementInstanceInstruction instruction =
        ImmutableAssertElementInstanceInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
            .state(ElementInstanceState.IS_ACTIVE)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(any());

    verify(processInstanceAssert).hasActiveElement(elementSelectorCaptor.capture(), eq(1));

    // Verify that the element selector applies the filter with elementId
    elementSelectorCaptor.getValue().applyFilter(elementInstanceFilter);
    verify(elementInstanceFilter).elementId(ELEMENT_ID);
  }

  @Test
  void shouldSelectElementByName() {
    // given
    final AssertElementInstanceInstruction instruction =
        ImmutableAssertElementInstanceInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(ImmutableElementSelector.builder().elementName(ELEMENT_NAME).build())
            .state(ElementInstanceState.IS_COMPLETED)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(any());

    verify(processInstanceAssert).hasCompletedElement(elementSelectorCaptor.capture(), eq(1));

    // Verify that the element selector applies the filter with elementName
    elementSelectorCaptor.getValue().applyFilter(elementInstanceFilter);
    verify(elementInstanceFilter).elementName(ELEMENT_NAME);
  }

  @Test
  void shouldFailIfProcessDefinitionIdIsNotSet() {
    // given
    final AssertElementInstanceInstruction instruction =
        ImmutableAssertElementInstanceInstruction.builder()
            .processInstanceSelector(ImmutableProcessInstanceSelector.builder().build())
            .elementSelector(ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
            .state(ElementInstanceState.IS_ACTIVE)
            .build();

    // when/then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    instruction, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required property: processDefinitionId");
  }

  @Test
  void shouldFailIfElementSelectorHasNeitherIdNorName() {
    // given
    final AssertElementInstanceInstruction instruction =
        ImmutableAssertElementInstanceInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(ImmutableElementSelector.builder().build())
            .state(ElementInstanceState.IS_ACTIVE)
            .build();

    // when/then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    instruction, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Element selector must have either elementId or elementName");
  }

  private static Stream<Arguments> stateAssertionArgumentStream() {
    return Stream.of(
        stateAssertionArguments(
            ElementInstanceState.IS_ACTIVE,
            (processInstanceAssert, amount) ->
                processInstanceAssert.hasActiveElement(any(ElementSelector.class), eq(amount))),
        stateAssertionArguments(
            ElementInstanceState.IS_COMPLETED,
            (processInstanceAssert, amount) ->
                processInstanceAssert.hasCompletedElement(any(ElementSelector.class), eq(amount))),
        stateAssertionArguments(
            ElementInstanceState.IS_TERMINATED,
            (processInstanceAssert, amount) ->
                processInstanceAssert.hasTerminatedElement(
                    any(ElementSelector.class), eq(amount))));
  }

  private static Arguments stateAssertionArguments(
      final ElementInstanceState state,
      final BiConsumer<ProcessInstanceAssert, Integer> verification) {
    return Arguments.of(state, verification);
  }
}
