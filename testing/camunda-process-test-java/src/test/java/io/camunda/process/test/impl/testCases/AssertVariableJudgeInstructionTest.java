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
package io.camunda.process.test.impl.testCases;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.api.testCases.ImmutableElementSelector;
import io.camunda.process.test.api.testCases.ImmutableProcessInstanceSelector;
import io.camunda.process.test.api.testCases.instructions.AssertVariableJudgeInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableAssertVariableJudgeInstruction;
import io.camunda.process.test.impl.testCases.instructions.AssertVariableJudgeInstructionHandler;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AssertVariableJudgeInstructionTest {

  private static final String PROCESS_DEFINITION_ID = "process";
  private static final String ELEMENT_ID = "task_A";
  private static final String ELEMENT_NAME = "Task A";
  private static final String VARIABLE_NAME = "agentResponse";
  private static final String EXPECTATION = "should contain a valid summary";

  @Mock private CamundaProcessTestContext processTestContext;
  @Mock private CamundaClient camundaClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private AssertionFacade assertionFacade;

  private final AssertVariableJudgeInstructionHandler instructionHandler =
      new AssertVariableJudgeInstructionHandler();

  @Test
  void shouldAssertGlobalVariable() {
    // given
    final AssertVariableJudgeInstruction instruction =
        ImmutableAssertVariableJudgeInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .variableName(VARIABLE_NAME)
            .expectation(EXPECTATION)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(any());
    verify(processInstanceAssert).hasVariableSatisfiesJudge(eq(VARIABLE_NAME), eq(EXPECTATION));

    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }

  @Test
  void shouldAssertLocalVariableByElementId() {
    // given
    final AssertVariableJudgeInstruction instruction =
        ImmutableAssertVariableJudgeInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
            .variableName(VARIABLE_NAME)
            .expectation(EXPECTATION)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(any());
    verify(processInstanceAssert)
        .hasLocalVariableSatisfiesJudge(
            any(ElementSelector.class), eq(VARIABLE_NAME), eq(EXPECTATION));

    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }

  @Test
  void shouldAssertLocalVariableByElementName() {
    // given
    final AssertVariableJudgeInstruction instruction =
        ImmutableAssertVariableJudgeInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(ImmutableElementSelector.builder().elementName(ELEMENT_NAME).build())
            .variableName(VARIABLE_NAME)
            .expectation(EXPECTATION)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(any());
    verify(processInstanceAssert)
        .hasLocalVariableSatisfiesJudge(
            any(ElementSelector.class), eq(VARIABLE_NAME), eq(EXPECTATION));

    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldApplyThresholdOverride() {
    // given
    final ProcessInstanceAssert mockAssert = assertionFacade.assertThatProcessInstance(any());
    when(mockAssert.withJudgeConfig(any(UnaryOperator.class))).thenReturn(mockAssert);

    final AssertVariableJudgeInstruction instruction =
        ImmutableAssertVariableJudgeInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .variableName(VARIABLE_NAME)
            .expectation(EXPECTATION)
            .threshold(0.8)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(mockAssert).withJudgeConfig(any(UnaryOperator.class));
    verify(mockAssert).hasVariableSatisfiesJudge(eq(VARIABLE_NAME), eq(EXPECTATION));
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldApplyCustomPromptOverride() {
    // given
    final ProcessInstanceAssert mockAssert = assertionFacade.assertThatProcessInstance(any());
    when(mockAssert.withJudgeConfig(any(UnaryOperator.class))).thenReturn(mockAssert);

    final AssertVariableJudgeInstruction instruction =
        ImmutableAssertVariableJudgeInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .variableName(VARIABLE_NAME)
            .expectation(EXPECTATION)
            .customPrompt("You are a financial data judge")
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(mockAssert).withJudgeConfig(any(UnaryOperator.class));
    verify(mockAssert).hasVariableSatisfiesJudge(eq(VARIABLE_NAME), eq(EXPECTATION));
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldApplyBothOverrides() {
    // given
    final ProcessInstanceAssert mockAssert = assertionFacade.assertThatProcessInstance(any());
    when(mockAssert.withJudgeConfig(any(UnaryOperator.class))).thenReturn(mockAssert);

    final AssertVariableJudgeInstruction instruction =
        ImmutableAssertVariableJudgeInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .variableName(VARIABLE_NAME)
            .expectation(EXPECTATION)
            .threshold(0.9)
            .customPrompt("Custom evaluation criteria")
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(mockAssert).withJudgeConfig(any(UnaryOperator.class));
    verify(mockAssert).hasVariableSatisfiesJudge(eq(VARIABLE_NAME), eq(EXPECTATION));
  }
}
