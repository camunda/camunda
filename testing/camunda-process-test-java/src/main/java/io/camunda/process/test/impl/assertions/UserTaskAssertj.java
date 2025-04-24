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
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.process.test.api.assertions.UserTaskAssert;
import io.camunda.process.test.api.assertions.UserTaskSelector;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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
  public UserTaskAssert isActive() {
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
    hasProperty(
        userTask -> assignee.trim().equalsIgnoreCase(userTask.getAssignee()),
        userTask ->
            String.format(
                "Expected [%s] to have assignee %s, but was %s",
                actual.describe(), assignee, userTask.getAssignee()));
    return this;
  }

  @Override
  public UserTaskAssert hasPriority(final int priority) {
    hasProperty(
        userTask -> priority == userTask.getPriority(),
        userTask ->
            String.format(
                "Expected [%s] to have priority %d, but was %d",
                actual.describe(), priority, userTask.getPriority()));
    return this;
  }

  @Override
  public UserTaskAssert hasElementId(final String elementId) {
    hasProperty(
        userTask -> elementId.trim().equalsIgnoreCase(userTask.getElementId()),
        userTask ->
            String.format(
                "Expected [%s] to have elementId %s, but was %s",
                actual.describe(), elementId, userTask.getElementId()));
    return this;
  }

  private void hasProperty(
      final Predicate<UserTask> assertionPredicate,
      final Function<UserTask, String> failureMessageFn) {

    awaitUserTaskAssertion(
        actual::applyFilter,
        userTask ->
            assertThat(userTask)
                .withFailMessage(failureMessageFn.apply(userTask))
                .matches(assertionPredicate));
  }

  private void hasUserTaskInState(final UserTaskState expectedState) {
    awaitUserTaskAssertion(
        actual::applyFilter,
        userTask ->
            assertThat(userTask.getState())
                .withFailMessage(
                    "Expected [%s] to be %s, but was %s",
                    actual.describe(), formatState(expectedState), formatState(userTask.getState()))
                .isEqualTo(expectedState));
  }

  private static List<UserTask> getUserTasksInState(
      final List<UserTask> userTasks, final UserTaskState state) {
    return userTasks.stream()
        .filter(userTask -> userTask.getState().equals(state))
        .collect(Collectors.toList());
  }

  private String formatState(final UserTaskState state) {
    if (state == null) {
      return "not activated";
    }

    return state.name().toLowerCase();
  }

  private UserTask awaitUserTask(final Consumer<UserTaskFilter> filter) {
    final AtomicReference<UserTask> actualUserTask = new AtomicReference<>();

    try {
      Awaitility.await()
          .ignoreException(ClientException.class)
          .untilAsserted(
              () -> dataSource.findUserTasks(filter),
              userTasks -> {
                final Optional<UserTask> userTask =
                    userTasks.stream().filter(actual::test).findFirst();
                assertThat(userTask).isPresent();
                actualUserTask.set(userTask.get());
              });
    } catch (final ConditionTimeoutException ignore) {
      fail("No user task [%s] found.", actual.describe());
    }

    return actualUserTask.get();
  }

  private void awaitUserTaskAssertion(
      final Consumer<UserTaskFilter> filter, final Consumer<UserTask> assertion) {

    final AtomicReference<String> failureMessage = new AtomicReference<>("?");
    try {
      Awaitility.await()
          .ignoreException(ClientException.class)
          .untilAsserted(
              () -> awaitUserTask(filter),
              userTask -> {
                try {
                  assertion.accept(userTask);
                } catch (final AssertionError e) {
                  failureMessage.set(e.getMessage());
                  throw e;
                }
              });
    } catch (final ConditionTimeoutException ignore) {
      fail(failureMessage.get());
    }
  }
}
