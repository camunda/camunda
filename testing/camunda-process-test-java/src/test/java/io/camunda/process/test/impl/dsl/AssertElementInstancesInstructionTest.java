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
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.dsl.ImmutableElementSelector;
import io.camunda.process.test.api.dsl.ImmutableProcessInstanceSelector;
import io.camunda.process.test.api.dsl.instructions.AssertElementInstancesInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableAssertElementInstancesInstruction;
import io.camunda.process.test.api.dsl.instructions.assertElementInstance.ElementInstanceState;
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

  @Mock private CamundaProcessTestContext processTestContext;
  @Mock private CamundaClient camundaClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private AssertionFacade assertionFacade;

  @Mock private ProcessInstanceFilter processInstanceFilter;
  @Captor private ArgumentCaptor<ProcessInstanceSelector> processInstanceSelectorCaptor;

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
  void shouldAssertElementsState(
      final ElementInstanceState state,
      final Consumer<ProcessInstanceAssert> expectedAssertion) {
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

    verifyNoMoreInteractions(camundaClient, processTestContext);
  }

  @Test
  void shouldFailIfProcessDefinitionIdIsNotSet() {
    // given
    final AssertElementInstancesInstruction instruction =
        ImmutableAssertElementInstancesInstruction.builder()
            .processInstanceSelector(ImmutableProcessInstanceSelector.builder().build())
            .addElementSelectors(ImmutableElementSelector.builder().elementId(ELEMENT_ID_1).build())
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
    final AssertElementInstancesInstruction instruction =
        ImmutableAssertElementInstancesInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .addElementSelectors(ImmutableElementSelector.builder().build())
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
            processInstanceAssert ->
                processInstanceAssert.hasActiveElements(any(ElementSelector[].class))),
        stateAssertionArguments(
            ElementInstanceState.IS_COMPLETED,
            processInstanceAssert ->
                processInstanceAssert.hasCompletedElements(any(ElementSelector[].class))),
        stateAssertionArguments(
            ElementInstanceState.IS_TERMINATED,
            processInstanceAssert ->
                processInstanceAssert.hasTerminatedElements(any(ElementSelector[].class))),
        stateAssertionArguments(
            ElementInstanceState.IS_NOT_ACTIVE,
            processInstanceAssert ->
                processInstanceAssert.hasNoActiveElements(any(ElementSelector[].class))),
        stateAssertionArguments(
            ElementInstanceState.IS_NOT_ACTIVATED,
            processInstanceAssert ->
                processInstanceAssert.hasNotActivatedElements(any(ElementSelector[].class))),
        stateAssertionArguments(
            ElementInstanceState.IS_ACTIVE_EXACTLY,
            processInstanceAssert ->
                processInstanceAssert.hasActiveElementsExactly(any(ElementSelector[].class))),
        stateAssertionArguments(
            ElementInstanceState.IS_COMPLETED_IN_ORDER,
            processInstanceAssert ->
                processInstanceAssert.hasCompletedElementsInOrder(any(ElementSelector[].class))));
  }

  private static Arguments stateAssertionArguments(
      final ElementInstanceState state, final Consumer<ProcessInstanceAssert> verification) {
    return Arguments.of(state, verification);
  }
}
