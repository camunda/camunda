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
import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.api.testCases.ImmutableElementSelector;
import io.camunda.process.test.api.testCases.ImmutableProcessInstanceSelector;
import io.camunda.process.test.api.testCases.instructions.AssertVariableInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableAssertVariableInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableSatisfiesJudge;
import io.camunda.process.test.impl.judge.JudgeConfigImpl;
import io.camunda.process.test.impl.testCases.instructions.AssertVariableInstructionHandler;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AssertVariableInstructionTest {

  private static final String PROCESS_DEFINITION_ID = "process";
  private static final String ELEMENT_ID = "task_A";
  private static final String VARIABLE_NAME = "agentResponse";
  private static final String EXPECTATION = "should contain a valid summary";

  @Mock private CamundaProcessTestContext processTestContext;
  @Mock private CamundaClient camundaClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private AssertionFacade assertionFacade;

  private final AssertVariableInstructionHandler instructionHandler =
      new AssertVariableInstructionHandler();

  @SuppressWarnings("unchecked")
  @Test
  void shouldApplyThresholdOverride() {
    // given
    final ProcessInstanceAssert mockAssert = assertionFacade.assertThatProcessInstance(any());
    when(mockAssert.withJudgeConfig(any(UnaryOperator.class))).thenReturn(mockAssert);

    final AssertVariableInstruction instruction =
        ImmutableAssertVariableInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .variableName(VARIABLE_NAME)
            .satisfiesJudge(
                ImmutableSatisfiesJudge.builder().expectation(EXPECTATION).threshold(0.8).build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    final ArgumentCaptor<UnaryOperator<JudgeConfig>> judgeConfigOverrideCaptor =
        ArgumentCaptor.forClass(UnaryOperator.class);
    verify(mockAssert).withJudgeConfig(judgeConfigOverrideCaptor.capture());

    final JudgeConfig updatedConfig =
        judgeConfigOverrideCaptor.getValue().apply(new JudgeConfigImpl(s -> s, 0.0, null));
    assertThat(updatedConfig.getThreshold()).isEqualTo(0.8);

    verify(mockAssert).hasVariableSatisfiesJudge(eq(VARIABLE_NAME), eq(EXPECTATION));
    verifyNoMoreInteractions(camundaClient, processTestContext, mockAssert);
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldApplyCustomPromptOverride() {
    // given
    final ProcessInstanceAssert mockAssert = assertionFacade.assertThatProcessInstance(any());
    when(mockAssert.withJudgeConfig(any(UnaryOperator.class))).thenReturn(mockAssert);

    final AssertVariableInstruction instruction =
        ImmutableAssertVariableInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .variableName(VARIABLE_NAME)
            .satisfiesJudge(
                ImmutableSatisfiesJudge.builder()
                    .expectation(EXPECTATION)
                    .customPrompt("You are a financial data judge")
                    .build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    final ArgumentCaptor<UnaryOperator<JudgeConfig>> judgeConfigOverrideCaptor =
        ArgumentCaptor.forClass(UnaryOperator.class);
    verify(mockAssert).withJudgeConfig(judgeConfigOverrideCaptor.capture());

    final JudgeConfig updatedConfig =
        judgeConfigOverrideCaptor.getValue().apply(new JudgeConfigImpl(s -> s, 0.0, null));
    assertThat(updatedConfig.getCustomPrompt()).hasValue("You are a financial data judge");

    verify(mockAssert).hasVariableSatisfiesJudge(eq(VARIABLE_NAME), eq(EXPECTATION));
    verifyNoMoreInteractions(camundaClient, processTestContext, mockAssert);
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldApplyBothOverrides() {
    // given
    final ProcessInstanceAssert mockAssert = assertionFacade.assertThatProcessInstance(any());
    when(mockAssert.withJudgeConfig(any(UnaryOperator.class))).thenReturn(mockAssert);

    final AssertVariableInstruction instruction =
        ImmutableAssertVariableInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .variableName(VARIABLE_NAME)
            .satisfiesJudge(
                ImmutableSatisfiesJudge.builder()
                    .expectation(EXPECTATION)
                    .threshold(0.9)
                    .customPrompt("Custom evaluation criteria")
                    .build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    final ArgumentCaptor<UnaryOperator<JudgeConfig>> judgeConfigOverrideCaptor =
        ArgumentCaptor.forClass(UnaryOperator.class);
    verify(mockAssert).withJudgeConfig(judgeConfigOverrideCaptor.capture());

    final JudgeConfig updatedConfig =
        judgeConfigOverrideCaptor.getValue().apply(new JudgeConfigImpl(s -> s, 0.0, null));
    assertThat(updatedConfig.getThreshold()).isEqualTo(0.9);
    assertThat(updatedConfig.getCustomPrompt()).hasValue("Custom evaluation criteria");

    verify(mockAssert).hasVariableSatisfiesJudge(eq(VARIABLE_NAME), eq(EXPECTATION));
    verifyNoMoreInteractions(camundaClient, processTestContext, mockAssert);
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldApplyOverridesForLocalVariable() {
    // given
    final ProcessInstanceAssert mockAssert = assertionFacade.assertThatProcessInstance(any());
    when(mockAssert.withJudgeConfig(any(UnaryOperator.class))).thenReturn(mockAssert);

    final AssertVariableInstruction instruction =
        ImmutableAssertVariableInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
            .variableName(VARIABLE_NAME)
            .satisfiesJudge(
                ImmutableSatisfiesJudge.builder()
                    .expectation(EXPECTATION)
                    .threshold(0.7)
                    .customPrompt("Local variable evaluation criteria")
                    .build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    final ArgumentCaptor<UnaryOperator<JudgeConfig>> judgeConfigOverrideCaptor =
        ArgumentCaptor.forClass(UnaryOperator.class);
    verify(mockAssert).withJudgeConfig(judgeConfigOverrideCaptor.capture());

    final JudgeConfig updatedConfig =
        judgeConfigOverrideCaptor.getValue().apply(new JudgeConfigImpl(s -> s, 0.0, null));
    assertThat(updatedConfig.getThreshold()).isEqualTo(0.7);
    assertThat(updatedConfig.getCustomPrompt()).hasValue("Local variable evaluation criteria");

    verify(mockAssert)
        .hasLocalVariableSatisfiesJudge(
            any(ElementSelector.class), eq(VARIABLE_NAME), eq(EXPECTATION));
    verifyNoMoreInteractions(camundaClient, processTestContext, mockAssert);
  }
}
