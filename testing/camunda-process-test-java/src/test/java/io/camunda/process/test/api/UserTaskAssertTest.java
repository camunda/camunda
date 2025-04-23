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

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.impl.search.response.UserTaskImpl;
import io.camunda.client.protocol.rest.UserTaskResult;
import io.camunda.client.protocol.rest.UserTaskResult.StateEnum;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UserTaskAssertTest {
  @Mock private CamundaDataSource camundaDataSource;

  @BeforeEach
  void configureAssertions() {
    CamundaAssert.initialize(camundaDataSource);
    CamundaAssert.setAssertionInterval(Duration.ZERO);
    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(1));
  }

  @AfterEach
  void resetAssertions() {
    CamundaAssert.setAssertionInterval(CamundaAssert.DEFAULT_ASSERTION_INTERVAL);
    CamundaAssert.setAssertionTimeout(CamundaAssert.DEFAULT_ASSERTION_TIMEOUT);
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
                        .state(StateEnum.CREATED)
                        .assignee("tester")
                        .priority(60))));

    // then
    assertThat(UserTaskSelectors.byName("a"))
        .isActive()
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
                          .state(StateEnum.CREATED))));

      // then
      assertThat(UserTaskSelectors.byName("a")).isActive();
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
                          .state(StateEnum.COMPLETED))));

      // then
      assertThat(UserTaskSelectors.byName("a")).isCompleted();
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
                          .state(StateEnum.CANCELED))));

      // then
      assertThat(UserTaskSelectors.byName("a")).isCanceled();
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
                          .state(StateEnum.FAILED))));

      // then
      assertThat(UserTaskSelectors.byName("a")).isFailed();
    }

    @Test
    public void isActiveForMultipleUserTasks() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Arrays.asList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("mult")
                          .elementInstanceKey("1")
                          .state(StateEnum.FAILED)),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("mult")
                          .elementInstanceKey("2")
                          .state(StateEnum.CREATED))));

      // then
      assertThat(UserTaskSelectors.byName("mult")).isActive();
    }

    @Test
    public void waitsForCorrectStatus() {
      // when
      final UserTask activeTask =
          new UserTaskImpl(
              new UserTaskResult().name("a").elementInstanceKey("1").state(StateEnum.CREATED));

      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.singletonList(activeTask));

      // then
      assertThat(UserTaskSelectors.byName("a")).isActive();
    }

    @Test
    public void failsWithConciseErrorMessageWhenNoTaskFound() {
      when(camundaDataSource.findUserTasks(any())).thenReturn(Collections.emptyList());

      // then
      Assertions.assertThatThrownBy(() -> assertThat(UserTaskSelectors.byName("a")).isActive())
          .hasMessage(
              "Expected at least one active user task [name: a], but none found. User tasks:\n<None>");
    }

    @Test
    public void failsWithConciseErrorMessageWhenTaskHasWrongState() {
      // when
      final UserTask completedTask =
          new UserTaskImpl(
              new UserTaskResult().name("a").elementInstanceKey("1").state(StateEnum.COMPLETED));

      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(Collections.singletonList(completedTask));

      // then
      Assertions.assertThatThrownBy(() -> assertThat(UserTaskSelectors.byName("a")).isActive())
          .hasMessage(
              "Expected at least one active user task [name: a], but none found. User tasks:\n"
                  + "\t- (instanceKey: 1): completed");
    }

    @Test
    public void displaysCreatedTasksAsActive() {
      // when
      final UserTask completedTask =
          new UserTaskImpl(
              new UserTaskResult().name("a").elementInstanceKey("1").state(StateEnum.CREATED));

      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(Collections.singletonList(completedTask));

      // then
      Assertions.assertThatThrownBy(() -> assertThat(UserTaskSelectors.byName("a")).isCanceled())
          .hasMessage(
              "Expected at least one canceled user task [name: a], but none found. User tasks:\n"
                  + "\t- (instanceKey: 1): active");
    }

    @Test
    public void formatsMultipleUserTasksInAssertionError() {
      // when
      final List<UserTask> tasks =
          Arrays.asList(
              new UserTaskImpl(
                  new UserTaskResult()
                      .name("a")
                      .elementInstanceKey("1")
                      .state(StateEnum.COMPLETED)),
              new UserTaskImpl(
                  new UserTaskResult().name("a").elementInstanceKey("2").state(StateEnum.FAILED)),
              new UserTaskImpl(
                  new UserTaskResult()
                      .name("a")
                      .elementInstanceKey("3")
                      .state(StateEnum.CANCELED)));

      when(camundaDataSource.findUserTasks(any())).thenReturn(tasks);

      // then
      Assertions.assertThatThrownBy(() -> assertThat(UserTaskSelectors.byName("a")).isActive())
          .hasMessage(
              "Expected at least one active user task [name: a], but none found. User tasks:\n"
                  + "\t- (instanceKey: 1): completed\n"
                  + "\t- (instanceKey: 2): failed\n"
                  + "\t- (instanceKey: 3): canceled");
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
                          .state(StateEnum.CREATED)
                          .priority(PRIORITY))));

      // then
      assertThat(UserTaskSelectors.byName("a")).hasPriority(PRIORITY);
    }

    @Test
    public void hasPriorityWithMultiple() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Arrays.asList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(StateEnum.CREATED)),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(StateEnum.CREATED)
                          .priority(40)),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(StateEnum.CREATED)
                          .priority(PRIORITY))));

      // then
      assertThat(UserTaskSelectors.byName("a")).hasPriority(PRIORITY);
    }

    @Test
    public void hasPriorityFailureMessage() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(StateEnum.CREATED)
                          .priority(40))));

      // then
      Assertions.assertThatThrownBy(
              () -> assertThat(UserTaskSelectors.byName("a")).hasPriority(PRIORITY))
          .hasMessage(
              "Expected at least one user task with priority 60, but found none. User tasks:\n"
                  + "\t- (instanceKey: 1): 40");
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
                          .state(StateEnum.CREATED)
                          .elementId(ELEMENT_ID))));

      // then
      assertThat(UserTaskSelectors.byName("a")).hasElementId(ELEMENT_ID);
    }

    @Test
    public void hasElementIdWithMultiple() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Arrays.asList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(StateEnum.CREATED)),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(StateEnum.CREATED)
                          .elementId("other")),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(StateEnum.CREATED)
                          .elementId(ELEMENT_ID))));

      // then
      assertThat(UserTaskSelectors.byName("a")).hasElementId(ELEMENT_ID);
    }

    @Test
    public void hasElementIdFailureMessage() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(StateEnum.CREATED)
                          .elementId("other_element_id"))));

      // then
      Assertions.assertThatThrownBy(
              () -> assertThat(UserTaskSelectors.byName("a")).hasElementId(ELEMENT_ID))
          .hasMessage(
              "Expected at least one user task with elementId element_id, but found none. User tasks:\n"
                  + "\t- (instanceKey: 1): other_element_id");
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
                          .state(StateEnum.CREATED)
                          .assignee(ASSIGNEE))));

      // then
      assertThat(UserTaskSelectors.byName("a")).hasAssignee(ASSIGNEE);
    }

    @Test
    public void hasAssigneeWithMultiple() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Arrays.asList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(StateEnum.CREATED)),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(StateEnum.CREATED)
                          .assignee("other")),
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(StateEnum.CREATED)
                          .assignee(ASSIGNEE))));

      // then
      assertThat(UserTaskSelectors.byName("a")).hasAssignee(ASSIGNEE);
    }

    @Test
    public void hasAssigneeFailureMesasge() {
      // when
      when(camundaDataSource.findUserTasks(any()))
          .thenReturn(
              Collections.singletonList(
                  new UserTaskImpl(
                      new UserTaskResult()
                          .name("a")
                          .elementInstanceKey("1")
                          .state(StateEnum.CREATED)
                          .assignee("other"))));

      // then
      Assertions.assertThatThrownBy(
              () -> assertThat(UserTaskSelectors.byName("a")).hasAssignee(ASSIGNEE))
          .hasMessage(
              "Expected at least one user task with assignee tester, but found none. User tasks:\n"
                  + "\t- (instanceKey: 1): other");
    }
  }
}
