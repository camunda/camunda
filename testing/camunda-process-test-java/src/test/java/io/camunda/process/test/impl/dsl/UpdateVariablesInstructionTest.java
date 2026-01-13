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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.dsl.ImmutableElementSelector;
import io.camunda.process.test.api.dsl.ImmutableProcessInstanceSelector;
import io.camunda.process.test.api.dsl.instructions.ImmutableUpdateVariablesInstruction;
import io.camunda.process.test.api.dsl.instructions.UpdateVariablesInstruction;
import io.camunda.process.test.impl.dsl.instructions.UpdateVariablesInstructionHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UpdateVariablesInstructionTest {

  private static final String PROCESS_DEFINITION_ID = "my-process";
  private static final long PROCESS_INSTANCE_KEY = 1234L;
  private static final long ELEMENT_INSTANCE_KEY = 5678L;
  private static final String ELEMENT_ID = "my-element";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  @Mock private ProcessInstance processInstance;

  @Mock private ElementInstance elementInstance;

  private final UpdateVariablesInstructionHandler instructionHandler =
      new UpdateVariablesInstructionHandler();

  @Test
  void shouldUpdateGlobalVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("status", "ready");
    variables.put("count", 42);

    final UpdateVariablesInstruction instruction =
        ImmutableUpdateVariablesInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .putAllVariables(variables)
            .build();

    when(camundaClient
            .newProcessInstanceSearchRequest()
            .filter(any(Consumer.class))
            .sort(any(Consumer.class))
            .page(any(Consumer.class))
            .send()
            .join()
            .items())
        .thenReturn(Collections.singletonList(processInstance));

    when(processInstance.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
    when(processInstance.getProcessDefinitionId()).thenReturn(PROCESS_DEFINITION_ID);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(camundaClient).newSetVariablesCommand(PROCESS_INSTANCE_KEY);
    verify(camundaClient.newSetVariablesCommand(PROCESS_INSTANCE_KEY)).variables(variables);
    verify(camundaClient.newSetVariablesCommand(PROCESS_INSTANCE_KEY).variables(variables))
        .send();
  }

  @Test
  void shouldUpdateLocalVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("localVar", "localValue");

    final UpdateVariablesInstruction instruction =
        ImmutableUpdateVariablesInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(
                ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
            .putAllVariables(variables)
            .build();

    when(camundaClient
            .newProcessInstanceSearchRequest()
            .filter(any(Consumer.class))
            .sort(any(Consumer.class))
            .page(any(Consumer.class))
            .send()
            .join()
            .items())
        .thenReturn(Collections.singletonList(processInstance));

    lenient().when(processInstance.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
    lenient().when(processInstance.getProcessDefinitionId()).thenReturn(PROCESS_DEFINITION_ID);

    when(camundaClient
            .newElementInstanceSearchRequest()
            .filter(any(Consumer.class))
            .sort(any(Consumer.class))
            .page(any(Consumer.class))
            .send()
            .join()
            .items())
        .thenReturn(Collections.singletonList(elementInstance));

    when(elementInstance.getElementInstanceKey()).thenReturn(ELEMENT_INSTANCE_KEY);
    when(elementInstance.getElementId()).thenReturn(ELEMENT_ID);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(camundaClient).newSetVariablesCommand(ELEMENT_INSTANCE_KEY);
    verify(camundaClient.newSetVariablesCommand(ELEMENT_INSTANCE_KEY)).variables(variables);
    verify(camundaClient.newSetVariablesCommand(ELEMENT_INSTANCE_KEY).variables(variables))
        .send();
  }

  @Test
  void shouldThrowExceptionWhenProcessInstanceNotFound() {
    // given
    final UpdateVariablesInstruction instruction =
        ImmutableUpdateVariablesInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .putVariables("key", "value")
            .build();

    when(camundaClient
            .newProcessInstanceSearchRequest()
            .filter(any(Consumer.class))
            .sort(any(Consumer.class))
            .page(any(Consumer.class))
            .send()
            .join()
            .items())
        .thenReturn(Collections.emptyList());

    // when / then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    instruction, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No process instance found for selector");
  }

  @Test
  void shouldThrowExceptionWhenElementInstanceNotFound() {
    // given
    final UpdateVariablesInstruction instruction =
        ImmutableUpdateVariablesInstruction.builder()
            .processInstanceSelector(
                ImmutableProcessInstanceSelector.builder()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .elementSelector(
                ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
            .putVariables("key", "value")
            .build();

    when(camundaClient
            .newProcessInstanceSearchRequest()
            .filter(any(Consumer.class))
            .sort(any(Consumer.class))
            .page(any(Consumer.class))
            .send()
            .join()
            .items())
        .thenReturn(Collections.singletonList(processInstance));

    lenient().when(processInstance.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
    lenient().when(processInstance.getProcessDefinitionId()).thenReturn(PROCESS_DEFINITION_ID);

    when(camundaClient
            .newElementInstanceSearchRequest()
            .filter(any(Consumer.class))
            .sort(any(Consumer.class))
            .page(any(Consumer.class))
            .send()
            .join()
            .items())
        .thenReturn(Collections.emptyList());

    // when / then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    instruction, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No element")
        .hasMessageContaining("found for process instance");
  }
}
