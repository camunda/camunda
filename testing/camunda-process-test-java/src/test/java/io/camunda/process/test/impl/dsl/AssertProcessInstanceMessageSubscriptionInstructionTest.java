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
import io.camunda.process.test.api.dsl.ImmutableMessageSelector;
import io.camunda.process.test.api.dsl.ImmutableProcessInstanceSelector;
import io.camunda.process.test.api.dsl.instructions.AssertProcessInstanceMessageSubscriptionInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableAssertProcessInstanceMessageSubscriptionInstruction;
import io.camunda.process.test.api.dsl.instructions.assertProcessInstanceMessageSubscription.MessageSubscriptionState;
import io.camunda.process.test.impl.dsl.instructions.AssertProcessInstanceMessageSubscriptionInstructionHandler;
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
public class AssertProcessInstanceMessageSubscriptionInstructionTest {

  private static final String PROCESS_DEFINITION_ID = "process";
  private static final String MESSAGE_NAME = "update";
  private static final String CORRELATION_KEY = "key-1";

  @Mock private CamundaProcessTestContext processTestContext;
  @Mock private CamundaClient camundaClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private AssertionFacade assertionFacade;

  @Mock private ProcessInstanceFilter processInstanceFilter;
  @Captor private ArgumentCaptor<ProcessInstanceSelector> processInstanceSelectorCaptor;

  private final AssertProcessInstanceMessageSubscriptionInstructionHandler instructionHandler =
      new AssertProcessInstanceMessageSubscriptionInstructionHandler();

  @Test
  void shouldSelectProcessInstanceByProcessDefinitionId() {
    // given
    final AssertProcessInstanceMessageSubscriptionInstruction instruction =
        ImmutableAssertProcessInstanceMessageSubscriptionInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .messageSelector(ImmutableMessageSelector.builder().messageName(MESSAGE_NAME).build())
            .state(MessageSubscriptionState.IS_WAITING)
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
  @MethodSource("messageSubscriptionStateArgumentStream")
  void shouldAssertMessageSubscriptionStateWithoutCorrelationKey(
      final MessageSubscriptionState state,
      final Consumer<ProcessInstanceAssert> expectedAssertion) {
    // given
    final AssertProcessInstanceMessageSubscriptionInstruction instruction =
        ImmutableAssertProcessInstanceMessageSubscriptionInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .messageSelector(ImmutableMessageSelector.builder().messageName(MESSAGE_NAME).build())
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

  @ParameterizedTest(name = "{0}")
  @MethodSource("messageSubscriptionStateWithCorrelationKeyArgumentStream")
  void shouldAssertMessageSubscriptionStateWithCorrelationKey(
      final MessageSubscriptionState state,
      final Consumer<ProcessInstanceAssert> expectedAssertion) {
    // given
    final AssertProcessInstanceMessageSubscriptionInstruction instruction =
        ImmutableAssertProcessInstanceMessageSubscriptionInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .messageSelector(
                ImmutableMessageSelector.builder()
                    .messageName(MESSAGE_NAME)
                    .correlationKey(CORRELATION_KEY)
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
  void shouldFailIfProcessDefinitionIdIsNotSet() {
    // given
    final AssertProcessInstanceMessageSubscriptionInstruction instruction =
        ImmutableAssertProcessInstanceMessageSubscriptionInstruction.builder()
            .processInstanceSelector(ImmutableProcessInstanceSelector.builder().build())
            .messageSelector(ImmutableMessageSelector.builder().messageName(MESSAGE_NAME).build())
            .state(MessageSubscriptionState.IS_WAITING)
            .build();

    // when/then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    instruction, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required property: processDefinitionId");
  }

  private static Stream<Arguments> messageSubscriptionStateArgumentStream() {
    return Stream.of(
        stateAssertionArguments(
            MessageSubscriptionState.IS_WAITING,
            processInstanceAssert -> processInstanceAssert.isWaitingForMessage(MESSAGE_NAME)),
        stateAssertionArguments(
            MessageSubscriptionState.IS_NOT_WAITING,
            processInstanceAssert -> processInstanceAssert.isNotWaitingForMessage(MESSAGE_NAME)),
        stateAssertionArguments(
            MessageSubscriptionState.IS_CORRELATED,
            processInstanceAssert -> processInstanceAssert.hasCorrelatedMessage(MESSAGE_NAME)));
  }

  private static Stream<Arguments> messageSubscriptionStateWithCorrelationKeyArgumentStream() {
    return Stream.of(
        stateAssertionArguments(
            MessageSubscriptionState.IS_WAITING,
            processInstanceAssert ->
                processInstanceAssert.isWaitingForMessage(MESSAGE_NAME, CORRELATION_KEY)),
        stateAssertionArguments(
            MessageSubscriptionState.IS_NOT_WAITING,
            processInstanceAssert ->
                processInstanceAssert.isNotWaitingForMessage(MESSAGE_NAME, CORRELATION_KEY)),
        stateAssertionArguments(
            MessageSubscriptionState.IS_CORRELATED,
            processInstanceAssert ->
                processInstanceAssert.hasCorrelatedMessage(MESSAGE_NAME, CORRELATION_KEY)));
  }

  private static Arguments stateAssertionArguments(
      final MessageSubscriptionState state, final Consumer<ProcessInstanceAssert> verification) {
    return Arguments.of(state, verification);
  }
}
