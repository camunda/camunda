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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.api.dsl.ImmutableElementSelector;
import io.camunda.process.test.api.dsl.ImmutableProcessInstanceSelector;
import io.camunda.process.test.api.dsl.instructions.AssertVariablesInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableAssertVariablesInstruction;
import io.camunda.process.test.impl.dsl.instructions.AssertVariablesInstructionHandler;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AssertVariablesInstructionTest {

  private static final String PROCESS_DEFINITION_ID = "process";
  private static final String ELEMENT_ID = "task_A";
  private static final String ELEMENT_NAME = "Task A";

  @Mock private CamundaProcessTestContext processTestContext;
  @Mock private CamundaClient camundaClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private AssertionFacade assertionFacade;

  private final AssertVariablesInstructionHandler instructionHandler =
      new AssertVariablesInstructionHandler();

  @Test
  void shouldAssertVariableNames() {
    // given
    final AssertVariablesInstruction instruction =
        ImmutableAssertVariablesInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .variableNames(Arrays.asList("var1", "var2"))
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(any());
    verify(processInstanceAssert).hasVariableNames("var1", "var2");

    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }

  @Test
  void shouldAssertVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("x", 3);
    variables.put("y", "okay");

    final AssertVariablesInstruction instruction =
        ImmutableAssertVariablesInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .variables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(any());
    verify(processInstanceAssert).hasVariables(eq(variables));

    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }

  @Test
  void shouldAssertLocalVariableNamesByElementId() {
    // given
    final AssertVariablesInstruction instruction =
        ImmutableAssertVariablesInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
            .variableNames(Arrays.asList("var1", "var2"))
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(any());
    verify(processInstanceAssert).hasLocalVariableNames(any(), eq("var1"), eq("var2"));

    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }

  @Test
  void shouldAssertLocalVariableNamesByElementName() {
    // given
    final AssertVariablesInstruction instruction =
        ImmutableAssertVariablesInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(ImmutableElementSelector.builder().elementName(ELEMENT_NAME).build())
            .variableNames(Arrays.asList("var1", "var2"))
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(any());
    verify(processInstanceAssert).hasLocalVariableNames(any(), eq("var1"), eq("var2"));

    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }

  @Test
  void shouldAssertLocalVariablesByElementId() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("x", 3);
    variables.put("y", "okay");

    final AssertVariablesInstruction instruction =
        ImmutableAssertVariablesInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
            .variables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(any());
    verify(processInstanceAssert).hasLocalVariables(any(), eq(variables));

    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }

  @Test
  void shouldAssertLocalVariablesByElementName() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("x", 3);
    variables.put("y", "okay");

    final AssertVariablesInstruction instruction =
        ImmutableAssertVariablesInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(ImmutableElementSelector.builder().elementName(ELEMENT_NAME).build())
            .variables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatProcessInstance(any());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(any());
    verify(processInstanceAssert).hasLocalVariables(any(), eq(variables));

    verifyNoMoreInteractions(camundaClient, processTestContext, processInstanceAssert);
  }
}
