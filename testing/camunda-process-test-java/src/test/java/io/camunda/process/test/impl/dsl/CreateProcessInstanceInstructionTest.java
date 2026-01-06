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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.dsl.ImmutableProcessDefinitionSelector;
import io.camunda.process.test.api.dsl.instructions.CreateProcessInstanceInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableCreateProcessInstanceInstruction;
import io.camunda.process.test.api.dsl.instructions.createProcessInstance.ImmutableCreateProcessInstanceStartInstruction;
import io.camunda.process.test.api.dsl.instructions.createProcessInstance.ImmutableCreateProcessInstanceTerminateRuntimeInstruction;
import io.camunda.process.test.impl.dsl.instructions.CreateProcessInstanceInstructionHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CreateProcessInstanceInstructionTest {

  private static final String PROCESS_DEFINITION_ID = "process";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  private final CreateProcessInstanceInstructionHandler instructionHandler =
      new CreateProcessInstanceInstructionHandler();

  @Test
  void shouldCreateProcessInstanceByProcessDefinitionId() {
    // given
    final CreateProcessInstanceInstruction instruction =
        ImmutableCreateProcessInstanceInstruction.builder()
            .processDefinitionSelector(
                ImmutableProcessDefinitionSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(camundaClient).newCreateInstanceCommand();

    final CreateProcessInstanceCommandStep3 mockCommand =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_DEFINITION_ID)
            .latestVersion()
            .variables(Collections.emptyMap());

    verify(mockCommand).send();

    verifyNoMoreInteractions(camundaClient, processTestContext, mockCommand, assertionFacade);
  }

  @Test
  void shouldSetVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("a", 1);
    variables.put("b", "two");

    final CreateProcessInstanceInstruction instruction =
        ImmutableCreateProcessInstanceInstruction.builder()
            .processDefinitionSelector(
                ImmutableProcessDefinitionSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(camundaClient).newCreateInstanceCommand();

    final CreateProcessInstanceCommandStep3 mockCommand =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_DEFINITION_ID)
            .latestVersion()
            .variables(variables);

    verify(mockCommand).send();

    verifyNoMoreInteractions(camundaClient, processTestContext, mockCommand, assertionFacade);
  }

  @Test
  void shouldSetStartInstructions() {
    // given
    final CreateProcessInstanceInstruction instruction =
        ImmutableCreateProcessInstanceInstruction.builder()
            .processDefinitionSelector(
                ImmutableProcessDefinitionSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .addStartInstructions(
                ImmutableCreateProcessInstanceStartInstruction.builder().elementId("task1").build())
            .addStartInstructions(
                ImmutableCreateProcessInstanceStartInstruction.builder().elementId("task2").build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(camundaClient).newCreateInstanceCommand();

    final CreateProcessInstanceCommandStep3 mockCommand =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_DEFINITION_ID)
            .latestVersion()
            .variables(Collections.emptyMap());

    verify(mockCommand).startBeforeElement("task1");
    verify(mockCommand).startBeforeElement("task2");
    verify(mockCommand).send();

    verifyNoMoreInteractions(camundaClient, processTestContext, mockCommand, assertionFacade);
  }

  @Test
  void shouldSetRuntimeInstructions() {
    // given
    final CreateProcessInstanceInstruction instruction =
        ImmutableCreateProcessInstanceInstruction.builder()
            .processDefinitionSelector(
                ImmutableProcessDefinitionSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .addRuntimeInstructions(
                ImmutableCreateProcessInstanceTerminateRuntimeInstruction.builder()
                    .afterElementId("task1")
                    .build())
            .addRuntimeInstructions(
                ImmutableCreateProcessInstanceTerminateRuntimeInstruction.builder()
                    .afterElementId("task2")
                    .build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(camundaClient).newCreateInstanceCommand();

    final CreateProcessInstanceCommandStep3 mockCommand =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_DEFINITION_ID)
            .latestVersion()
            .variables(Collections.emptyMap());

    verify(mockCommand).terminateAfterElement("task1");
    verify(mockCommand).terminateAfterElement("task2");
    verify(mockCommand).send();

    verifyNoMoreInteractions(camundaClient, processTestContext, mockCommand, assertionFacade);
  }

  @Test
  void shouldFailIfProcessDefinitionIdIsNotSet() {
    // given
    final CreateProcessInstanceInstruction instruction =
        ImmutableCreateProcessInstanceInstruction.builder()
            .processDefinitionSelector(ImmutableProcessDefinitionSelector.builder().build())
            .build();

    // when/then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    instruction, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required property: processDefinitionId");
  }
}
