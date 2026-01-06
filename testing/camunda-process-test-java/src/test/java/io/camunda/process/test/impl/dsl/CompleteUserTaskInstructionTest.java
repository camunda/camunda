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
import io.camunda.process.test.api.assertions.UserTaskSelector;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import io.camunda.process.test.api.dsl.ImmutableUserTaskSelector;
import io.camunda.process.test.api.dsl.instructions.CompleteUserTaskInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableCompleteUserTaskInstruction;
import io.camunda.process.test.impl.dsl.instructions.CompleteUserTaskInstructionHandler;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CompleteUserTaskInstructionTest {

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

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
    final ArgumentCaptor<UserTaskSelector> selectorCaptor =
        ArgumentCaptor.forClass(UserTaskSelector.class);
    verify(processTestContext).completeUserTask(selectorCaptor.capture(), eq(Map.of()));

    final UserTaskSelector capturedSelector = selectorCaptor.getValue();
    final UserTaskSelector expectedSelector = UserTaskSelectors.byTaskName("Approve Request");
    assertSelectorsAreEqual(capturedSelector, expectedSelector);

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
    final ArgumentCaptor<UserTaskSelector> selectorCaptor =
        ArgumentCaptor.forClass(UserTaskSelector.class);
    verify(processTestContext).completeUserTask(selectorCaptor.capture(), eq(Map.of()));

    final UserTaskSelector capturedSelector = selectorCaptor.getValue();
    final UserTaskSelector expectedSelector = UserTaskSelectors.byElementId("task1");
    assertSelectorsAreEqual(capturedSelector, expectedSelector);

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
    final ArgumentCaptor<UserTaskSelector> selectorCaptor =
        ArgumentCaptor.forClass(UserTaskSelector.class);
    verify(processTestContext).completeUserTask(selectorCaptor.capture(), eq(Map.of()));

    final UserTaskSelector capturedSelector = selectorCaptor.getValue();
    final UserTaskSelector expectedSelector =
        UserTaskSelectors.byProcessDefinitionId("my-process");
    assertSelectorsAreEqual(capturedSelector, expectedSelector);

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
    final ArgumentCaptor<UserTaskSelector> selectorCaptor =
        ArgumentCaptor.forClass(UserTaskSelector.class);
    verify(processTestContext).completeUserTask(selectorCaptor.capture(), eq(Map.of()));

    final UserTaskSelector capturedSelector = selectorCaptor.getValue();
    final UserTaskSelector expectedSelector =
        UserTaskSelectors.byProcessDefinitionId("my-process")
            .and(UserTaskSelectors.byElementId("task1"))
            .and(UserTaskSelectors.byTaskName("Review Task"));
    assertSelectorsAreEqual(capturedSelector, expectedSelector);

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
    final ArgumentCaptor<UserTaskSelector> selectorCaptor =
        ArgumentCaptor.forClass(UserTaskSelector.class);
    verify(processTestContext).completeUserTask(selectorCaptor.capture(), eq(variables));

    final UserTaskSelector capturedSelector = selectorCaptor.getValue();
    final UserTaskSelector expectedSelector = UserTaskSelectors.byElementId("task1");
    assertSelectorsAreEqual(capturedSelector, expectedSelector);

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldCompleteUserTaskWithExampleData() {
    // given
    final CompleteUserTaskInstruction instruction =
        ImmutableCompleteUserTaskInstruction.builder()
            .userTaskSelector(ImmutableUserTaskSelector.builder().elementId("task1").build())
            .withExampleData(true)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    final ArgumentCaptor<UserTaskSelector> selectorCaptor =
        ArgumentCaptor.forClass(UserTaskSelector.class);
    verify(processTestContext).completeUserTaskWithExampleData(selectorCaptor.capture());

    final UserTaskSelector capturedSelector = selectorCaptor.getValue();
    final UserTaskSelector expectedSelector = UserTaskSelectors.byElementId("task1");
    assertSelectorsAreEqual(capturedSelector, expectedSelector);

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
            .withExampleData(true)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    final ArgumentCaptor<UserTaskSelector> selectorCaptor =
        ArgumentCaptor.forClass(UserTaskSelector.class);
    verify(processTestContext).completeUserTaskWithExampleData(selectorCaptor.capture());

    final UserTaskSelector capturedSelector = selectorCaptor.getValue();
    final UserTaskSelector expectedSelector = UserTaskSelectors.byElementId("task1");
    assertSelectorsAreEqual(capturedSelector, expectedSelector);

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  private void assertSelectorsAreEqual(
      final UserTaskSelector actual, final UserTaskSelector expected) {
    // Compare selectors by their string representation since UserTaskSelector
    // implementations may not implement equals()
    if (!actual.describe().equals(expected.describe())) {
      throw new AssertionError(
          String.format(
              "Expected selector [%s] but got [%s]", expected.describe(), actual.describe()));
    }
  }
}
