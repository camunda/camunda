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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.UserTaskAssert;
import io.camunda.process.test.api.assertions.UserTaskSelector;
import io.camunda.process.test.api.dsl.ImmutableUserTaskSelector;
import io.camunda.process.test.api.dsl.instructions.AssertUserTaskInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableAssertUserTaskInstruction;
import io.camunda.process.test.api.dsl.instructions.assertUserTask.UserTaskState;
import io.camunda.process.test.impl.dsl.instructions.AssertUserTaskInstructionHandler;
import java.util.Arrays;
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
public class AssertUserTaskInstructionTest {

  @Mock private CamundaProcessTestContext processTestContext;
  @Mock private CamundaClient camundaClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private AssertionFacade assertionFacade;

  @Mock private UserTaskFilter userTaskFilter;
  @Captor private ArgumentCaptor<UserTaskSelector> userTaskSelectorCaptor;

  private final AssertUserTaskInstructionHandler instructionHandler =
      new AssertUserTaskInstructionHandler();

  @Test
  void shouldSelectUserTaskByTaskName() {
    // given
    final AssertUserTaskInstruction instruction =
        ImmutableAssertUserTaskInstruction.builder()
            .userTaskSelector(
                ImmutableUserTaskSelector.builder().taskName("Approve Request").build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatUserTask(userTaskSelectorCaptor.capture());

    userTaskSelectorCaptor.getValue().applyFilter(userTaskFilter);
    verify(userTaskFilter).name("Approve Request");

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldSelectUserTaskByElementId() {
    // given
    final AssertUserTaskInstruction instruction =
        ImmutableAssertUserTaskInstruction.builder()
            .userTaskSelector(ImmutableUserTaskSelector.builder().elementId("task1").build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatUserTask(userTaskSelectorCaptor.capture());

    userTaskSelectorCaptor.getValue().applyFilter(userTaskFilter);
    verify(userTaskFilter).elementId("task1");

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldSelectUserTaskByProcessDefinitionId() {
    // given
    final AssertUserTaskInstruction instruction =
        ImmutableAssertUserTaskInstruction.builder()
            .userTaskSelector(
                ImmutableUserTaskSelector.builder().processDefinitionId("my-process").build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatUserTask(userTaskSelectorCaptor.capture());

    userTaskSelectorCaptor.getValue().applyFilter(userTaskFilter);
    verify(userTaskFilter).bpmnProcessId("my-process");

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("stateAssertionArgumentStream")
  void shouldAssertState(
      final UserTaskState state, final Consumer<UserTaskAssert> expectedAssertion) {
    // given
    final AssertUserTaskInstruction instruction =
        ImmutableAssertUserTaskInstruction.builder()
            .userTaskSelector(ImmutableUserTaskSelector.builder().elementId("task1").build())
            .state(state)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatUserTask(any());

    final UserTaskAssert userTaskAssert = assertionFacade.assertThatUserTask(any());
    expectedAssertion.accept(verify(userTaskAssert));

    verifyNoMoreInteractions(camundaClient, processTestContext, userTaskAssert);
  }

  @Test
  void shouldAssertAssignee() {
    // given
    final AssertUserTaskInstruction instruction =
        ImmutableAssertUserTaskInstruction.builder()
            .userTaskSelector(ImmutableUserTaskSelector.builder().elementId("task1").build())
            .assignee("john")
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatUserTask(any());

    final UserTaskAssert userTaskAssert = assertionFacade.assertThatUserTask(any());
    verify(userTaskAssert).hasAssignee("john");

    verifyNoMoreInteractions(camundaClient, processTestContext, userTaskAssert);
  }

  @Test
  void shouldAssertCandidateGroups() {
    // given
    final AssertUserTaskInstruction instruction =
        ImmutableAssertUserTaskInstruction.builder()
            .userTaskSelector(ImmutableUserTaskSelector.builder().elementId("task1").build())
            .candidateGroups(Arrays.asList("managers", "supervisors"))
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatUserTask(any());

    final UserTaskAssert userTaskAssert = assertionFacade.assertThatUserTask(any());
    verify(userTaskAssert).hasCandidateGroups(Arrays.asList("managers", "supervisors"));

    verifyNoMoreInteractions(camundaClient, processTestContext, userTaskAssert);
  }

  @Test
  void shouldAssertPriority() {
    // given
    final AssertUserTaskInstruction instruction =
        ImmutableAssertUserTaskInstruction.builder()
            .userTaskSelector(ImmutableUserTaskSelector.builder().elementId("task1").build())
            .priority(100)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatUserTask(any());

    final UserTaskAssert userTaskAssert = assertionFacade.assertThatUserTask(any());
    verify(userTaskAssert).hasPriority(100);

    verifyNoMoreInteractions(camundaClient, processTestContext, userTaskAssert);
  }

  @Test
  void shouldAssertElementId() {
    // given
    final AssertUserTaskInstruction instruction =
        ImmutableAssertUserTaskInstruction.builder()
            .userTaskSelector(ImmutableUserTaskSelector.builder().taskName("Review").build())
            .elementId("task1")
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatUserTask(any());

    final UserTaskAssert userTaskAssert = assertionFacade.assertThatUserTask(any());
    verify(userTaskAssert).hasElementId("task1");

    verifyNoMoreInteractions(camundaClient, processTestContext, userTaskAssert);
  }

  @Test
  void shouldAssertName() {
    // given
    final AssertUserTaskInstruction instruction =
        ImmutableAssertUserTaskInstruction.builder()
            .userTaskSelector(ImmutableUserTaskSelector.builder().elementId("task1").build())
            .name("Review Task")
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatUserTask(any());

    final UserTaskAssert userTaskAssert = assertionFacade.assertThatUserTask(any());
    verify(userTaskAssert).hasName("Review Task");

    verifyNoMoreInteractions(camundaClient, processTestContext, userTaskAssert);
  }

  @Test
  void shouldAssertDueDate() {
    // given
    final AssertUserTaskInstruction instruction =
        ImmutableAssertUserTaskInstruction.builder()
            .userTaskSelector(ImmutableUserTaskSelector.builder().elementId("task1").build())
            .dueDate("2025-12-31T23:59:59Z")
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatUserTask(any());

    final UserTaskAssert userTaskAssert = assertionFacade.assertThatUserTask(any());
    verify(userTaskAssert).hasDueDate("2025-12-31T23:59:59Z");

    verifyNoMoreInteractions(camundaClient, processTestContext, userTaskAssert);
  }

  @Test
  void shouldAssertFollowUpDate() {
    // given
    final AssertUserTaskInstruction instruction =
        ImmutableAssertUserTaskInstruction.builder()
            .userTaskSelector(ImmutableUserTaskSelector.builder().elementId("task1").build())
            .followUpDate("2025-12-15T10:00:00Z")
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(assertionFacade).assertThatUserTask(any());

    final UserTaskAssert userTaskAssert = assertionFacade.assertThatUserTask(any());
    verify(userTaskAssert).hasFollowUpDate("2025-12-15T10:00:00Z");

    verifyNoMoreInteractions(camundaClient, processTestContext, userTaskAssert);
  }

  private static Stream<Arguments> stateAssertionArgumentStream() {
    return Stream.of(
        stateAssertionArguments(UserTaskState.IS_CREATED, UserTaskAssert::isCreated),
        stateAssertionArguments(UserTaskState.IS_COMPLETED, UserTaskAssert::isCompleted),
        stateAssertionArguments(UserTaskState.IS_CANCELED, UserTaskAssert::isCanceled),
        stateAssertionArguments(UserTaskState.IS_FAILED, UserTaskAssert::isFailed));
  }

  private static Arguments stateAssertionArguments(
      final UserTaskState state, final Consumer<UserTaskAssert> verification) {
    return Arguments.of(state, verification);
  }
}
