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
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.dsl.ImmutableProcessInstanceSelector;
import io.camunda.process.test.api.dsl.instructions.AssertProcessInstanceInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableAssertProcessInstanceInstruction;
import io.camunda.process.test.api.dsl.instructions.assertProcessInstance.ProcessInstanceState;
import io.camunda.process.test.impl.dsl.instructions.AssertProcessInstanceInstructionHandler;
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
public class AssertProcessInstanceInstructionTest {

  private static final String PROCESS_DEFINITION_ID = "process";

  @Mock private CamundaProcessTestContext processTestContext;
  @Mock private CamundaClient camundaClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private AssertionFacade assertionFacade;

  @Mock private ProcessInstanceFilter processInstanceFilter;
  @Captor private ArgumentCaptor<ProcessInstanceSelector> processInstanceSelectorCaptor;

  private final AssertProcessInstanceInstructionHandler instructionHandler =
      new AssertProcessInstanceInstructionHandler();

  @Test
  void shouldSelectProcessInstanceByProcessDefinitionId() {
    // given
    final AssertProcessInstanceInstruction instruction =
        ImmutableAssertProcessInstanceInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
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
  void shouldAssertState(
      final ProcessInstanceState state, final Consumer<ProcessInstanceAssert> expectedAssertion) {
    // given
    final AssertProcessInstanceInstruction instruction =
        ImmutableAssertProcessInstanceInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
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
  void shouldAssertHasActiveIncidents() {
    // given
    final AssertProcessInstanceInstruction instruction =
        ImmutableAssertProcessInstanceInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .hasActiveIncidents(true)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(any());
    verify(processInstanceAssert).hasActiveIncidents();

    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }

  @Test
  void shouldAssertHasNoActiveIncidents() {
    // given
    final AssertProcessInstanceInstruction instruction =
        ImmutableAssertProcessInstanceInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .hasActiveIncidents(false)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(any());
    verify(processInstanceAssert).hasNoActiveIncidents();

    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }

  @Test
  void shouldFailIfProcessDefinitionIdIsNotSet() {
    // given
    final AssertProcessInstanceInstruction instruction =
        ImmutableAssertProcessInstanceInstruction.builder()
            .processInstanceSelector(ImmutableProcessInstanceSelector.builder().build())
            .build();

    // when/then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    instruction, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required property: processDefinitionId");
  }

  private static Stream<Arguments> stateAssertionArgumentStream() {
    return Stream.of(
        stateAssertionArguments(ProcessInstanceState.IS_ACTIVE, ProcessInstanceAssert::isActive),
        stateAssertionArguments(
            ProcessInstanceState.IS_COMPLETED, ProcessInstanceAssert::isCompleted),
        stateAssertionArguments(
            ProcessInstanceState.IS_TERMINATED, ProcessInstanceAssert::isTerminated),
        stateAssertionArguments(ProcessInstanceState.IS_CREATED, ProcessInstanceAssert::isCreated));
  }

  private static Arguments stateAssertionArguments(
      final ProcessInstanceState state, final Consumer<ProcessInstanceAssert> verification) {
    return Arguments.of(state, verification);
  }
}
