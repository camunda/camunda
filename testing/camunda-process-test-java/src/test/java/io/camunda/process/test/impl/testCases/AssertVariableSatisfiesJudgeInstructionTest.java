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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.api.testCases.ImmutableElementSelector;
import io.camunda.process.test.api.testCases.ImmutableProcessInstanceSelector;
import io.camunda.process.test.api.testCases.instructions.AssertVariableSatisfiesJudgeInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableAssertVariableSatisfiesJudgeInstruction;
import io.camunda.process.test.impl.judge.JudgeConfigImpl;
import io.camunda.process.test.impl.testCases.instructions.AssertVariableSatisfiesJudgeInstructionHandler;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AssertVariableSatisfiesJudgeInstructionTest {

  private static final String PROCESS_DEFINITION_ID = "process";
  private static final String ELEMENT_ID = "task_A";
  private static final String ELEMENT_NAME = "Task A";
  private static final String VARIABLE_NAME = "agentResponse";
  private static final String EXPECTATION = "should contain a valid summary";

  @Mock private CamundaProcessTestContext processTestContext;
  @Mock private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ProcessInstanceAssert processInstanceAssert;

  private final AssertVariableSatisfiesJudgeInstructionHandler instructionHandler =
      new AssertVariableSatisfiesJudgeInstructionHandler();

  @Test
  void shouldAssertGlobalVariable() {
    // given
    final AssertVariableSatisfiesJudgeInstruction instruction =
        ImmutableAssertVariableSatisfiesJudgeInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .variableName(VARIABLE_NAME)
            .expectation(EXPECTATION)
            .build();

    when(assertionFacade.assertThatProcessInstance(any(ProcessInstanceSelector.class)))
        .thenReturn(processInstanceAssert);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processInstanceAssert).hasVariableSatisfiesJudge(eq(VARIABLE_NAME), eq(EXPECTATION));
    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }

  @Test
  void shouldAssertLocalVariableByElementId() {
    // given
    final AssertVariableSatisfiesJudgeInstruction instruction =
        ImmutableAssertVariableSatisfiesJudgeInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
            .variableName(VARIABLE_NAME)
            .expectation(EXPECTATION)
            .build();
    when(assertionFacade.assertThatProcessInstance(any(ProcessInstanceSelector.class)))
        .thenReturn(processInstanceAssert);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processInstanceAssert)
        .hasLocalVariableSatisfiesJudge(
            any(ElementSelector.class), eq(VARIABLE_NAME), eq(EXPECTATION));
    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }

  @Test
  void shouldAssertLocalVariableByElementName() {
    // given
    final AssertVariableSatisfiesJudgeInstruction instruction =
        ImmutableAssertVariableSatisfiesJudgeInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(ImmutableElementSelector.builder().elementName(ELEMENT_NAME).build())
            .variableName(VARIABLE_NAME)
            .expectation(EXPECTATION)
            .build();
    when(assertionFacade.assertThatProcessInstance(any(ProcessInstanceSelector.class)))
        .thenReturn(processInstanceAssert);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());
    verify(processInstanceAssert)
        .hasLocalVariableSatisfiesJudge(
            any(ElementSelector.class), eq(VARIABLE_NAME), eq(EXPECTATION));

    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldApplyThresholdOverride() {
    // given
    when(assertionFacade.assertThatProcessInstance(any(ProcessInstanceSelector.class)))
        .thenReturn(processInstanceAssert);
    when(processInstanceAssert.withJudgeConfig(any(UnaryOperator.class)))
        .thenReturn(processInstanceAssert);

    final AssertVariableSatisfiesJudgeInstruction instruction =
        ImmutableAssertVariableSatisfiesJudgeInstruction.builder()
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
    final ArgumentCaptor<UnaryOperator<JudgeConfig>> judgeConfigOverrideCaptor =
        ArgumentCaptor.forClass(UnaryOperator.class);
    verify(processInstanceAssert).withJudgeConfig(judgeConfigOverrideCaptor.capture());

    final JudgeConfig updatedConfig =
        judgeConfigOverrideCaptor.getValue().apply(new JudgeConfigImpl(s -> s, 0.0, null));
    assertThat(updatedConfig.getThreshold()).isEqualTo(0.8);

    verify(processInstanceAssert).hasVariableSatisfiesJudge(eq(VARIABLE_NAME), eq(EXPECTATION));
    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldApplyCustomPromptOverride() {
    // given
    when(assertionFacade.assertThatProcessInstance(any(ProcessInstanceSelector.class)))
        .thenReturn(processInstanceAssert);
    when(processInstanceAssert.withJudgeConfig(any(UnaryOperator.class)))
        .thenReturn(processInstanceAssert);

    final AssertVariableSatisfiesJudgeInstruction instruction =
        ImmutableAssertVariableSatisfiesJudgeInstruction.builder()
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
    final ArgumentCaptor<UnaryOperator<JudgeConfig>> judgeConfigOverrideCaptor =
        ArgumentCaptor.forClass(UnaryOperator.class);
    verify(processInstanceAssert).withJudgeConfig(judgeConfigOverrideCaptor.capture());

    final JudgeConfig updatedConfig =
        judgeConfigOverrideCaptor.getValue().apply(new JudgeConfigImpl(s -> s, 0.0, null));
    assertThat(updatedConfig.getCustomPrompt()).hasValue("You are a financial data judge");

    verify(processInstanceAssert).hasVariableSatisfiesJudge(eq(VARIABLE_NAME), eq(EXPECTATION));
    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldApplyBothOverrides() {
    // given
    when(assertionFacade.assertThatProcessInstance(any(ProcessInstanceSelector.class)))
        .thenReturn(processInstanceAssert);
    when(processInstanceAssert.withJudgeConfig(any(UnaryOperator.class)))
        .thenReturn(processInstanceAssert);

    final AssertVariableSatisfiesJudgeInstruction instruction =
        ImmutableAssertVariableSatisfiesJudgeInstruction.builder()
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
    final ArgumentCaptor<UnaryOperator<JudgeConfig>> judgeConfigOverrideCaptor =
        ArgumentCaptor.forClass(UnaryOperator.class);
    verify(processInstanceAssert).withJudgeConfig(judgeConfigOverrideCaptor.capture());

    final JudgeConfig updatedConfig =
        judgeConfigOverrideCaptor.getValue().apply(new JudgeConfigImpl(s -> s, 0.0, null));
    assertThat(updatedConfig.getThreshold()).isEqualTo(0.9);
    assertThat(updatedConfig.getCustomPrompt()).hasValue("Custom evaluation criteria");

    verify(processInstanceAssert).hasVariableSatisfiesJudge(eq(VARIABLE_NAME), eq(EXPECTATION));
    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldApplyOverridesForLocalVariable() {
    // given
    when(assertionFacade.assertThatProcessInstance(any(ProcessInstanceSelector.class)))
        .thenReturn(processInstanceAssert);
    when(processInstanceAssert.withJudgeConfig(any(UnaryOperator.class)))
        .thenReturn(processInstanceAssert);

    final AssertVariableSatisfiesJudgeInstruction instruction =
        ImmutableAssertVariableSatisfiesJudgeInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
            .variableName(VARIABLE_NAME)
            .expectation(EXPECTATION)
            .threshold(0.7)
            .customPrompt("Local variable evaluation criteria")
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    final ArgumentCaptor<UnaryOperator<JudgeConfig>> judgeConfigOverrideCaptor =
        ArgumentCaptor.forClass(UnaryOperator.class);
    verify(processInstanceAssert).withJudgeConfig(judgeConfigOverrideCaptor.capture());

    final JudgeConfig updatedConfig =
        judgeConfigOverrideCaptor.getValue().apply(new JudgeConfigImpl(s -> s, 0.0, null));
    assertThat(updatedConfig.getThreshold()).isEqualTo(0.7);
    assertThat(updatedConfig.getCustomPrompt()).hasValue("Local variable evaluation criteria");

    verify(processInstanceAssert)
        .hasLocalVariableSatisfiesJudge(
            any(ElementSelector.class), eq(VARIABLE_NAME), eq(EXPECTATION));
    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }
}
