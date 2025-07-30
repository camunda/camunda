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
package io.camunda.process.test.api;

import static io.camunda.process.test.api.CamundaAssert.assertThatUserTask;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.impl.search.response.UserTaskImpl;
import io.camunda.client.protocol.rest.UserTaskResult;
import io.camunda.client.protocol.rest.UserTaskStateEnum;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.utils.CamundaAssertExpectFailure;
import io.camunda.process.test.utils.CamundaAssertExtension;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({CamundaAssertExtension.class, MockitoExtension.class})
public class UserTaskAssertTest {
  @Mock private CamundaDataSource camundaDataSource;

  @BeforeEach
  void configureAssertions() {
    CamundaAssert.initialize(camundaDataSource);
  }

  @Test
  public void canChainMultipleAssertions() {
    // when
    when(camundaDataSource.findUserTasks(any()))
        .thenReturn(
            Collections.singletonList(
                new UserTaskImpl(
                    new UserTaskResult()
                        .name("a")
                        .elementInstanceKey("1")
                        .elementId("test_element")
                        .state(UserTaskStateEnum.CREATED)
                        .assignee("tester")
                        .priority(60))));

    // then
    assertThatUserTask(UserTaskSelectors.byTaskName("a"))
        .isCreated()
        .hasElementId("test_element")
        .hasPriority(60)
        .hasAssignee("tester");
  }

  @Nested
  public class UserTaskStates {
    @Test
    public void isActive() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).isCreated();
    }

    @Test
    public void isCompleted() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.COMPLETED))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).isCompleted();
    }

    @Test
    public void isCanceled() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CANCELED))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).isCanceled();
    }

    @Test
    public void isFailed() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.FAILED))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).isFailed();
    }

    @Test
    public void isActiveForMultipleUserTasks() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Arrays.asList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.FAILED)),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("mult")
                          .elementInstanceKey("2")
                          .state(UserTaskStateEnum.CREATED))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("mult")).isCreated();
    }

    @Test
    public void waitsForCorrectStatus() {
      // when
      final UserTask activeTask =
          new UserTaskImpl(
              new UserTaskResult()
                  .name("a")
                  .elementInstanceKey("1")
                  .state(UserTaskStateEnum.CREATED));

      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.singletonList(activeTask));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).isCreated();
    }

    @Test
    @CamundaAssertExpectFailure
    public void failsWithConciseErrorMessageWhenNoTaskFound() {
      when(camundaDataSource.findUserTasks(any())).thenReturn(Collections.emptyList());

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatUserTask(UserTaskSelectors.byTaskName("a")).isCreated())
          .hasMessage("No user task [a] found");
    }

    @Test
    @CamundaAssertExpectFailure
    public void failsWithConciseErrorMessageWhenTaskHasWrongState() {
      // when
      final UserTask completedTask =
          new UserTaskImpl(
              new UserTaskResult()
                  .name("a")
                  .elementInstanceKey("1")
                  .state(UserTaskStateEnum.COMPLETED));

      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(Collections.singletonList(completedTask));

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatUserTask(UserTaskSelectors.byTaskName("a")).isCreated())
          .hasMessage("Expected [a] to be created, but was completed");
    }
  }

  @Nested
  public class Priority {
    private static final int PRIORITY = 60;

    @Test
    public void hasPriority() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .priority(PRIORITY))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasPriority(PRIORITY);
    }

    @Test
    public void hasPriorityWithMultiple() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Arrays.asList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("b")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("c")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .priority(40)),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .priority(PRIORITY))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasPriority(PRIORITY);
    }

    @Test
    @CamundaAssertExpectFailure
    public void hasPriorityFailureMessage() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .priority(40))));

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasPriority(PRIORITY))
          .hasMessage("Expected [a] to have priority '60', but was '40'");
    }
  }

  @Nested
  public class ElementId {
    private static final String ELEMENT_ID = "element_id";

    @Test
    public void hasElementId() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .elementId(ELEMENT_ID))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasElementId(ELEMENT_ID);
    }

    @Test
    public void hasElementIdWithMultiple() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Arrays.asList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("b")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("c")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .elementId("other")),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .elementId(ELEMENT_ID))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasElementId(ELEMENT_ID);
    }

    @Test
    @CamundaAssertExpectFailure
    public void hasElementIdFailureMessage() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .elementId("other_element_id"))));

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasElementId(ELEMENT_ID))
          .hasMessage("Expected [a] to have element ID 'element_id', but was 'other_element_id'");
    }
  }

  @Nested
  public class Name {
    private static final String NAME = "name";

    @Test
    public void hasName() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .name(NAME))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName(NAME)).hasName(NAME);
    }

    @Test
    public void hasNameWithMultiple() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Arrays.asList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("b")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("c")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .name("other")),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .name(NAME))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName(NAME)).hasName(NAME);
    }

    @Test
    @CamundaAssertExpectFailure
    public void hasNameFailureMessage() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name(NAME)
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED))));

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatUserTask(UserTaskSelectors.byTaskName(NAME)).hasName("other_name"))
          .hasMessage("Expected [name] to have name 'other_name', but was 'name'");
    }
  }

  @Nested
  public class Assignee {
    private static final String ASSIGNEE = "tester";

    @Test
    public void hasAssignee() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .assignee(ASSIGNEE))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasAssignee(ASSIGNEE);
    }

    @Test
    public void hasAssigneeWithMultiple() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Arrays.asList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("b")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("c")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .assignee("other")),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .assignee(ASSIGNEE))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasAssignee(ASSIGNEE);
    }

    @Test
    @CamundaAssertExpectFailure
    public void hasAssigneeFailureMessage() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .assignee("other"))));

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasAssignee(ASSIGNEE))
          .hasMessage("Expected [a] to have assignee 'tester', but was 'other'");
    }
  }

  @Nested
  public class DueDate {
    private static final String DUE_DATE = "2025-05-01T10:00:00.000Z";

    @Test
    public void hasDueDate() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .dueDate(DUE_DATE))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasDueDate(DUE_DATE);
    }

    @Test
    public void hasDueDateWithMultiple() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Arrays.asList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("b")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("c")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .dueDate("2025-04-30T10:00:00.000Z")),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .dueDate(DUE_DATE))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasDueDate(DUE_DATE);
    }

    @Test
    @CamundaAssertExpectFailure
    public void hasDueDateFailureMessage() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .dueDate("2025-04-30T10:00:00.000Z"))));

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasDueDate(DUE_DATE))
          .hasMessage(
              "Expected [a] to have due date '2025-05-01T10:00:00.000Z', but was '2025-04-30T10:00:00.000Z'");
    }
  }

  @Nested
  public class CompletionDate {
    private static final String COMPLETION_DATE = "2025-05-01T10:00:00.000Z";

    @Test
    public void hasCompletionDate() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .completionDate(COMPLETION_DATE))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasCompletionDate(COMPLETION_DATE);
    }

    @Test
    public void hasCompletionDateWithMultiple() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Arrays.asList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("b")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("c")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .completionDate("2025-04-30T10:00:00.000Z")),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .completionDate(COMPLETION_DATE))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasCompletionDate(COMPLETION_DATE);
    }

    @Test
    @CamundaAssertExpectFailure
    public void hasCompletionDateFailureMessage() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .completionDate("2025-04-30T10:00:00.000Z"))));

      // then
      Assertions.assertThatThrownBy(
              () ->
                  assertThatUserTask(UserTaskSelectors.byTaskName("a"))
                      .hasCompletionDate(COMPLETION_DATE))
          .hasMessage(
              "Expected [a] to have completion date '2025-05-01T10:00:00.000Z', but was '2025-04-30T10:00:00.000Z'");
    }
  }

  @Nested
  public class FollowUpDate {
    private static final String FOLLOW_UP_DATE = "2025-05-01T10:00:00.000Z";

    @Test
    public void hasFollowUpDate() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .followUpDate(FOLLOW_UP_DATE))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasFollowUpDate(FOLLOW_UP_DATE);
    }

    @Test
    public void hasFollowUpDateWithMultiple() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Arrays.asList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("b")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("c")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .followUpDate("2025-04-30T10:00:00.000Z")),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .followUpDate(FOLLOW_UP_DATE))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasFollowUpDate(FOLLOW_UP_DATE);
    }

    @Test
    @CamundaAssertExpectFailure
    public void hasFollowUpDateFailureMessage() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .followUpDate("2025-04-30T10:00:00.000Z"))));

      // then
      Assertions.assertThatThrownBy(
              () ->
                  assertThatUserTask(UserTaskSelectors.byTaskName("a"))
                      .hasFollowUpDate(FOLLOW_UP_DATE))
          .hasMessage(
              "Expected [a] to have follow-up date '2025-05-01T10:00:00.000Z', but was '2025-04-30T10:00:00.000Z'");
    }
  }

  @Nested
  public class CreationDate {
    private static final String CREATION_DATE = "2025-05-01T10:00:00.000Z";

    @Test
    public void hasCreationDate() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .creationDate(CREATION_DATE))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasCreationDate(CREATION_DATE);
    }

    @Test
    public void hasCreationDateWithMultiple() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Arrays.asList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("b")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("c")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .creationDate("2025-04-30T10:00:00.000Z")),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .creationDate(CREATION_DATE))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasCreationDate(CREATION_DATE);
    }

    @Test
    @CamundaAssertExpectFailure
    public void hasCreationDateFailureMessage() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .creationDate("2025-04-30T10:00:00.000Z"))));

      // then
      Assertions.assertThatThrownBy(
              () ->
                  assertThatUserTask(UserTaskSelectors.byTaskName("a"))
                      .hasCreationDate(CREATION_DATE))
          .hasMessage(
              "Expected [a] to have creation date '2025-05-01T10:00:00.000Z', but was '2025-04-30T10:00:00.000Z'");
    }
  }

  @Nested
  public class CandidateGroups {
    private static final String CANDIDATE_GROUP = "engineering";
    private final List<String> candidateGroups = Arrays.asList("engineering", "management");

    @Test
    public void hasCandidateGroup() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .candidateGroups(candidateGroups))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasCandidateGroup(CANDIDATE_GROUP);
    }

    @Test
    public void hasCandidateGroups() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .candidateGroups(candidateGroups))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasCandidateGroups(candidateGroups);
    }

    @Test
    public void hasCandidateGroupsAllowsSubset() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .candidateGroups(
                              Arrays.asList("hr", "engineering", "management", "legal")))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasCandidateGroups(candidateGroups);
    }

    @Test
    public void hasCandidateGroupWithMultiple() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Arrays.asList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("b")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("c")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .candidateGroups(Collections.singletonList("marketing"))),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .candidateGroups(candidateGroups))));

      // then
      assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasCandidateGroup(CANDIDATE_GROUP);
    }

    @Test
    @CamundaAssertExpectFailure
    public void hasCandidateGroupFailureMessage() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .candidateGroups(candidateGroups))));

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatUserTask(UserTaskSelectors.byTaskName("a")).hasCandidateGroup("foo"))
          .hasMessage(
              "Expected [a] to have candidate group 'foo', but was [engineering, management]");
    }

    @Test
    @CamundaAssertExpectFailure
    public void hasCandidateGroupsFailureMessage() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(UserTaskStateEnum.CREATED)
                          .candidateGroups(Collections.singletonList("marketing")))));

      // then
      Assertions.assertThatThrownBy(
              () ->
                  assertThatUserTask(UserTaskSelectors.byTaskName("a"))
                      .hasCandidateGroups(candidateGroups))
          .hasMessage(
              "Expected [a] to have candidate groups [engineering, management], but was [marketing]");
    }
  }
}
