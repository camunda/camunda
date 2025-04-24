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
package io.camunda.process.test.impl.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.process.test.api.assertions.UserTaskAssert;
import io.camunda.process.test.api.assertions.UserTaskSelector;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.AbstractAssert;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

public class UserTaskAssertj extends AbstractAssert<UserTaskAssertj, UserTaskSelector>
    implements UserTaskAssert {

  private final CamundaDataSource dataSource;

  public UserTaskAssertj(final CamundaDataSource dataSource, final UserTaskSelector selector) {
    super(selector, UserTaskAssert.class);
    this.dataSource = dataSource;
  }

  @Override
  public UserTaskAssert isCreated() {
    hasUserTaskInState(UserTaskState.CREATED);
    return this;
  }

  @Override
  public UserTaskAssert isCompleted() {
    hasUserTaskInState(UserTaskState.COMPLETED);
    return this;
  }

  @Override
  public UserTaskAssert isCanceled() {
    hasUserTaskInState(UserTaskState.CANCELED);
    return this;
  }

  @Override
  public UserTaskAssert isFailed() {
    hasUserTaskInState(UserTaskState.FAILED);
    return this;
  }

  @Override
  public UserTaskAssert hasAssignee(final String assignee) {
    final UserTask userTask = awaitUserTask();

    assertThat(userTask.getAssignee())
        .withFailMessage(
            "Expected [%s] to have assignee %s, but was %s",
            actual.describe(), assignee, userTask.getAssignee())
        .isEqualTo(assignee);

    return this;
  }

  @Override
  public UserTaskAssert hasPriority(final int priority) {
    final UserTask userTask = awaitUserTask();

    assertThat(userTask.getPriority())
        .withFailMessage(
            "Expected [%s] to have priority %d, but was %d",
            actual.describe(), priority, userTask.getPriority())
        .isEqualTo(priority);

    return this;
  }

  @Override
  public UserTaskAssert hasElementId(final String elementId) {
    final UserTask userTask = awaitUserTask();

    assertThat(userTask.getElementId().trim())
        .withFailMessage(
            "Expected [%s] to have elementId %s, but was %s",
            actual.describe(), elementId, userTask.getElementId())
        .isEqualTo(elementId);

    return this;
  }

  @Override
  public UserTaskAssert hasProcessInstanceKey(final long processInstanceKey) {
    final UserTask userTask = awaitUserTask();

    assertThat(userTask.getProcessInstanceKey())
        .withFailMessage(
            "Expected [%s] to have processInstanceKey %d, but was %d",
            actual.describe(), processInstanceKey, userTask.getProcessInstanceKey())
        .isEqualTo(processInstanceKey);

    return this;
  }

  private void hasUserTaskInState(final UserTaskState expectedState) {
    final UserTask userTask = awaitUserTask();

    assertThat(userTask.getState())
        .withFailMessage(
            "Expected [%s] to be %s, but was %s",
            actual.describe(), formatState(expectedState), formatState(userTask.getState()))
        .isEqualTo(expectedState);
  }

  private String formatState(final UserTaskState state) {
    if (state == null) {
      return "not activated";
    }

    return state.name().toLowerCase();
  }

  private UserTask awaitUserTask() {
    final AtomicReference<UserTask> actualUserTask = new AtomicReference<>();

    try {
      Awaitility.await()
          .ignoreException(ClientException.class)
          .untilAsserted(
              () -> dataSource.findUserTasks(actual::applyFilter),
              userTasks -> {
                final Optional<UserTask> userTask =
                    userTasks.stream().filter(actual::test).findFirst();
                assertThat(userTask).isPresent();
                actualUserTask.set(userTask.get());
              });
    } catch (final ConditionTimeoutException ignore) {
      fail("No user task [%s] found", actual.describe());
    }

    return actualUserTask.get();
  }
}
