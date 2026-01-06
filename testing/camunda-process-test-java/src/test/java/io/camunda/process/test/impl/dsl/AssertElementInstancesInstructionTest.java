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
import io.camunda.process.test.api.dsl.instructions.AssertElementInstancesInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableAssertElementInstancesInstruction;
import io.camunda.process.test.api.dsl.instructions.assertElementInstances.ElementInstancesState;
import io.camunda.process.test.impl.dsl.instructions.AssertElementInstancesInstructionHandler;
import java.util.function.Consumer;
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
public class AssertElementInstancesInstructionTest {

  private static final String PROCESS_DEFINITION_ID = "process";
  private static final String ELEMENT_ID_1 = "task1";
  private static final String ELEMENT_ID_2 = "task2";
  private static final String ELEMENT_NAME = "Task A";

  @Mock private CamundaProcessTestContext processTestContext;
  @Mock private CamundaClient camundaClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private AssertionFacade assertionFacade;

  @Mock private ProcessInstanceFilter processInstanceFilter;
  @Mock private ElementInstanceFilter elementInstanceFilter;
  @Captor private ArgumentCaptor<ProcessInstanceSelector> processInstanceSelectorCaptor;
  @Captor private ArgumentCaptor<ElementSelector> elementSelectorCaptor;

  private final AssertElementInstancesInstructionHandler instructionHandler =
      new AssertElementInstancesInstructionHandler();

  @Test
  void shouldSelectProcessInstanceByProcessDefinitionId() {
    // given
    final AssertElementInstancesInstruction instruction =
        ImmutableAssertElementInstancesInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .addElementSelectors(ImmutableElementSelector.builder().elementId(ELEMENT_ID_1).build())
            .addElementSelectors(ImmutableElementSelector.builder().elementId(ELEMENT_ID_2).build())
            .state(ElementInstancesState.IS_ACTIVE)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(processInstanceSelectorCaptor.capture());

    processInstanceSelectorCaptor.getValue().applyFilter(processInstanceFilter);
    verify(processInstanceFilter).processDefinitionId(PROCESS_DEFINITION_ID);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("stateAssertionArgumentStream")
  void shouldAssertElementsState(
      final ElementInstancesState state, final Consumer<ProcessInstanceAssert> expectedAssertion) {
    // given
    final AssertElementInstancesInstruction instruction =
        ImmutableAssertElementInstancesInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .addElementSelectors(ImmutableElementSelector.builder().elementId(ELEMENT_ID_1).build())
            .addElementSelectors(ImmutableElementSelector.builder().elementId(ELEMENT_ID_2).build())
            .state(state)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(any());

    expectedAssertion.accept(verify(processInstanceAssert));

    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }

  @Test
  void shouldFailIfProcessDefinitionIdIsNotSet() {
    // given
    final AssertElementInstancesInstruction instruction =
        ImmutableAssertElementInstancesInstruction.builder()
            .processInstanceSelector(ImmutableProcessInstanceSelector.builder().build())
            .addElementSelectors(ImmutableElementSelector.builder().elementId(ELEMENT_ID_1).build())
            .state(ElementInstancesState.IS_ACTIVE)
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
    final AssertElementInstancesInstruction instruction =
        ImmutableAssertElementInstancesInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .addElementSelectors(ImmutableElementSelector.builder().build())
            .state(ElementInstancesState.IS_ACTIVE)
            .build();

    // when/then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    instruction, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Element selector must have either elementId or elementName");
  }

  @Test
  void shouldSelectElementById() {
    // given
    final AssertElementInstancesInstruction instruction =
        ImmutableAssertElementInstancesInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .addElementSelectors(ImmutableElementSelector.builder().elementId(ELEMENT_ID_1).build())
            .state(ElementInstancesState.IS_ACTIVE)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(any());

    verify(processInstanceAssert).hasActiveElements(elementSelectorCaptor.capture());

    // Verify that the element selector applies the filter with elementId
    elementSelectorCaptor.getValue().applyFilter(elementInstanceFilter);
    verify(elementInstanceFilter).elementId(ELEMENT_ID_1);
  }

  @Test
  void shouldSelectElementByName() {
    // given
    final AssertElementInstancesInstruction instruction =
        ImmutableAssertElementInstancesInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .addElementSelectors(
                ImmutableElementSelector.builder().elementName(ELEMENT_NAME).build())
            .state(ElementInstancesState.IS_COMPLETED)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(any());

    verify(processInstanceAssert).hasCompletedElements(elementSelectorCaptor.capture());

    // Verify that the element selector applies the filter with elementName
    elementSelectorCaptor.getValue().applyFilter(elementInstanceFilter);
    verify(elementInstanceFilter).elementName(ELEMENT_NAME);
  }

  private static Stream<Arguments> stateAssertionArgumentStream() {
    return Stream.of(
        stateAssertionArguments(
            ElementInstancesState.IS_ACTIVE,
            processInstanceAssert ->
                processInstanceAssert.hasActiveElements(any(ElementSelector[].class))),
        stateAssertionArguments(
            ElementInstancesState.IS_COMPLETED,
            processInstanceAssert ->
                processInstanceAssert.hasCompletedElements(any(ElementSelector[].class))),
        stateAssertionArguments(
            ElementInstancesState.IS_TERMINATED,
            processInstanceAssert ->
                processInstanceAssert.hasTerminatedElements(any(ElementSelector[].class))),
        stateAssertionArguments(
            ElementInstancesState.IS_NOT_ACTIVE,
            processInstanceAssert ->
                processInstanceAssert.hasNoActiveElements(any(ElementSelector[].class))),
        stateAssertionArguments(
            ElementInstancesState.IS_NOT_ACTIVATED,
            processInstanceAssert ->
                processInstanceAssert.hasNotActivatedElements(any(ElementSelector[].class))),
        stateAssertionArguments(
            ElementInstancesState.IS_ACTIVE_EXACTLY,
            processInstanceAssert ->
                processInstanceAssert.hasActiveElementsExactly(any(ElementSelector[].class))),
        stateAssertionArguments(
            ElementInstancesState.IS_COMPLETED_IN_ORDER,
            processInstanceAssert ->
                processInstanceAssert.hasCompletedElementsInOrder(any(ElementSelector[].class))));
  }

  private static Arguments stateAssertionArguments(
      final ElementInstancesState state, final Consumer<ProcessInstanceAssert> verification) {
    return Arguments.of(state, verification);
  }
}
