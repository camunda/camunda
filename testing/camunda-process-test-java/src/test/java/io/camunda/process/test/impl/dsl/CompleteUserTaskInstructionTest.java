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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.UserTaskSelector;
import io.camunda.process.test.api.dsl.ImmutableUserTaskSelector;
import io.camunda.process.test.api.dsl.instructions.CompleteUserTaskInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableCompleteUserTaskInstruction;
import io.camunda.process.test.impl.dsl.instructions.CompleteUserTaskInstructionHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CompleteUserTaskInstructionTest {

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  @Mock private UserTaskFilter userTaskFilter;

  @Captor private ArgumentCaptor<UserTaskSelector> selectorCaptor;

  private final CompleteUserTaskInstructionHandler instructionHandler =
      new CompleteUserTaskInstructionHandler();

  @Test
  void shouldCompleteUserTaskByTaskName() {
    // given
    final CompleteUserTaskInstruction instruction =
        ImmutableCompleteUserTaskInstruction.builder()
            .userTaskSelector(
                ImmutableUserTaskSelector.builder().taskName("Approve Request").build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeUserTask(selectorCaptor.capture(), eq(Collections.emptyMap()));

    selectorCaptor.getValue().applyFilter(userTaskFilter);
    verify(userTaskFilter).taskName("Approve Request");

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldCompleteUserTaskByElementId() {
    // given
    final CompleteUserTaskInstruction instruction =
        ImmutableCompleteUserTaskInstruction.builder()
            .userTaskSelector(ImmutableUserTaskSelector.builder().elementId("task1").build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeUserTask(selectorCaptor.capture(), eq(Collections.emptyMap()));

    selectorCaptor.getValue().applyFilter(userTaskFilter);
    verify(userTaskFilter).elementId("task1");

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldCompleteUserTaskByProcessDefinitionId() {
    // given
    final CompleteUserTaskInstruction instruction =
        ImmutableCompleteUserTaskInstruction.builder()
            .userTaskSelector(
                ImmutableUserTaskSelector.builder().processDefinitionId("my-process").build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeUserTask(selectorCaptor.capture(), eq(Collections.emptyMap()));

    selectorCaptor.getValue().applyFilter(userTaskFilter);
    verify(userTaskFilter).processDefinitionId("my-process");

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldCompleteUserTaskWithCombinedSelector() {
    // given
    final CompleteUserTaskInstruction instruction =
        ImmutableCompleteUserTaskInstruction.builder()
            .userTaskSelector(
                ImmutableUserTaskSelector.builder()
                    .processDefinitionId("my-process")
                    .elementId("task1")
                    .taskName("Review Task")
                    .build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeUserTask(selectorCaptor.capture(), eq(Collections.emptyMap()));

    selectorCaptor.getValue().applyFilter(userTaskFilter);
    verify(userTaskFilter).processDefinitionId("my-process");
    verify(userTaskFilter).elementId("task1");
    verify(userTaskFilter).taskName("Review Task");

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldCompleteUserTaskWithVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("approved", true);
    variables.put("comment", "Looks good");

    final CompleteUserTaskInstruction instruction =
        ImmutableCompleteUserTaskInstruction.builder()
            .userTaskSelector(ImmutableUserTaskSelector.builder().elementId("task1").build())
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).completeUserTask(selectorCaptor.capture(), eq(variables));

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldCompleteUserTaskWithExampleData() {
    // given
    final CompleteUserTaskInstruction instruction =
        ImmutableCompleteUserTaskInstruction.builder()
            .userTaskSelector(ImmutableUserTaskSelector.builder().elementId("task1").build())
            .useExampleData(true)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).completeUserTaskWithExampleData(selectorCaptor.capture());

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldUseExampleDataOverVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("x", 1);
    variables.put("y", 2);

    final CompleteUserTaskInstruction instruction =
        ImmutableCompleteUserTaskInstruction.builder()
            .userTaskSelector(ImmutableUserTaskSelector.builder().elementId("task1").build())
            .putAllVariables(variables)
            .useExampleData(true)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).completeUserTaskWithExampleData(selectorCaptor.capture());

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }
}
