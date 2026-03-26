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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.api.testCases.ImmutableElementSelector;
import io.camunda.process.test.api.testCases.ImmutableProcessInstanceSelector;
import io.camunda.process.test.api.testCases.instructions.AssertVariableSimilarToInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableAssertVariableSimilarToInstruction;
import io.camunda.process.test.impl.testCases.instructions.AssertVariableSimilarToInstructionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AssertVariableSimilarToInstructionTest {

  private static final String PROCESS_DEFINITION_ID = "process";
  private static final String ELEMENT_ID = "task_A";
  private static final String VARIABLE_NAME = "result";
  private static final String EXPECTED_VALUE = "the expected answer";
  private static final double THRESHOLD = 0.85;

  @Mock private CamundaProcessTestContext processTestContext;
  @Mock private CamundaClient camundaClient;
  @Mock private AssertionFacade assertionFacade;
  @Mock private ProcessInstanceAssert processInstanceAssert;

  private final AssertVariableSimilarToInstructionHandler instructionHandler =
      new AssertVariableSimilarToInstructionHandler();

  @BeforeEach
  void setUp() {
    when(assertionFacade.assertThatProcessInstance(any())).thenReturn(processInstanceAssert);
  }

  @Test
  void shouldAssertGlobalVariable() {
    // given
    final AssertVariableSimilarToInstruction instruction =
        ImmutableAssertVariableSimilarToInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .variableName(VARIABLE_NAME)
            .expectedValue(EXPECTED_VALUE)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());
    verify(processInstanceAssert, never()).withSemanticSimilarityConfig(any());
    verify(processInstanceAssert).hasVariableSimilarTo(VARIABLE_NAME, EXPECTED_VALUE);

    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }

  @Test
  void shouldAssertGlobalVariableWithThreshold() {
    // given
    when(processInstanceAssert.withSemanticSimilarityConfig(any())).thenReturn(processInstanceAssert);

    final AssertVariableSimilarToInstruction instruction =
        ImmutableAssertVariableSimilarToInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .variableName(VARIABLE_NAME)
            .expectedValue(EXPECTED_VALUE)
            .threshold(THRESHOLD)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());
    verify(processInstanceAssert).withSemanticSimilarityConfig(any());
    verify(processInstanceAssert).hasVariableSimilarTo(VARIABLE_NAME, EXPECTED_VALUE);

    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }

  @Test
  void shouldAssertLocalVariable() {
    // given
    final AssertVariableSimilarToInstruction instruction =
        ImmutableAssertVariableSimilarToInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
            .variableName(VARIABLE_NAME)
            .expectedValue(EXPECTED_VALUE)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());
    verify(processInstanceAssert, never()).withSemanticSimilarityConfig(any());
    verify(processInstanceAssert)
        .hasLocalVariableSimilarTo(
            any(ElementSelector.class), eq(VARIABLE_NAME), eq(EXPECTED_VALUE));

    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }

  @Test
  void shouldAssertLocalVariableWithThreshold() {
    // given
    when(processInstanceAssert.withSemanticSimilarityConfig(any())).thenReturn(processInstanceAssert);

    final AssertVariableSimilarToInstruction instruction =
        ImmutableAssertVariableSimilarToInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
            .variableName(VARIABLE_NAME)
            .expectedValue(EXPECTED_VALUE)
            .threshold(THRESHOLD)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());
    verify(processInstanceAssert).withSemanticSimilarityConfig(any());
    verify(processInstanceAssert)
        .hasLocalVariableSimilarTo(
            any(ElementSelector.class), eq(VARIABLE_NAME), eq(EXPECTED_VALUE));

    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }
}
