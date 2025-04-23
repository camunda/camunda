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
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import java.util.List;
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
  private final UserTaskSelector selector;

  private final AtomicReference<UserTask> actualUserTask = new AtomicReference<>();

  public UserTaskAssertj(final CamundaDataSource dataSource, final String taskName) {
    this(dataSource, UserTaskSelectors.byName(taskName));
  }

  public UserTaskAssertj(
      final CamundaDataSource dataSource, final String taskName, final long processInstanceKey) {
    this(dataSource, UserTaskSelectors.byName(taskName, processInstanceKey));
  }

  public UserTaskAssertj(final CamundaDataSource dataSource, final UserTaskSelector selector) {
    super(selector, UserTaskAssert.class);

    this.dataSource = dataSource;
    this.selector = selector;
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
    hasAtLeastOneUserTaskWithProperty(
        userTask -> assignee.trim().equalsIgnoreCase(userTask.getAssignee()),
        String.format("Expected at least one user task with assignee %s, but found none", assignee),
        userTask ->
            String.format(
                "\t- (instanceKey: %d): %s",
                userTask.getElementInstanceKey(), userTask.getAssignee()));
    return this;
  }

  @Override
  public UserTaskAssert hasPriority(final int priority) {
    hasAtLeastOneUserTaskWithProperty(
        userTask -> priority == userTask.getPriority(),
        String.format("Expected at least one user task with priority %d, but found none", priority),
        userTask ->
            String.format(
                "\t- (instanceKey: %d): %d",
                userTask.getElementInstanceKey(), userTask.getPriority()));
    return this;
  }

  @Override
  public UserTaskAssert hasElementId(final String elementId) {
    hasAtLeastOneUserTaskWithProperty(
        userTask -> elementId.trim().equalsIgnoreCase(userTask.getElementId()),
        String.format(
            "Expected at least one user task with elementId %s, but found none", elementId),
        userTask ->
            String.format(
                "\t- (instanceKey: %d): %s",
                userTask.getElementInstanceKey(), userTask.getElementId()));
    return this;
  }

  private void hasAtLeastOneUserTaskWithProperty(
      final Predicate<UserTask> assertionPredicate,
      final String failureMessageBase,
      final Function<UserTask, String> userTaskPrinter) {

    awaitUserTaskAssertion(
        selector::applyFilter,
        userTasks -> {
          final List<UserTask> relevantUserTasks =
              userTasks.stream().filter(selector::test).collect(Collectors.toList());

          assertThat(relevantUserTasks)
              .withFailMessage(
                  formatPropertyAssertionErrorMessage(
                      relevantUserTasks, failureMessageBase, userTaskPrinter))
              .anyMatch(assertionPredicate);
        });
  }

  private String formatPropertyAssertionErrorMessage(
      final List<UserTask> tasks,
      final String failureMessageBase,
      final Function<UserTask, String> taskPrinter) {

    return String.format(
        "%s. User tasks:\n%s",
        failureMessageBase,
        tasks.isEmpty()
            ? "<None>"
            : tasks.stream().map(taskPrinter).collect(Collectors.joining("\n")));
  }

  private void hasUserTaskInState(final UserTaskState expectedState) {
    awaitUserTaskAssertion(
        selector::applyFilter,
        userTasks -> {
          final List<UserTask> relevantUserTasks =
              userTasks.stream().filter(selector::test).collect(Collectors.toList());
          final List<UserTask> activeUserTasks =
              getUserTasksInState(relevantUserTasks, expectedState);

          assertThat(activeUserTasks)
              .withFailMessage(
                  "Expected at least one %s user task [name: %s], but none found. User tasks:\n%s",
                  formatState(expectedState),
                  selector.describe(),
                  relevantUserTasks.isEmpty()
                      ? "<None>"
                      : relevantUserTasks.stream()
                          .map(
                              ut ->
                                  String.format(
                                      "\t- (instanceKey: %d): %s",
                                      ut.getElementInstanceKey(), formatState(ut.getState())))
                          .collect(Collectors.joining("\n")))
              .isNotEmpty();
        });
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

    switch (state) {
      case UNKNOWN_ENUM_VALUE:
        return "not activated";

      case CREATED:
        return "active";

      default:
        return state.name().toLowerCase();
    }
  }

  private void awaitUserTaskAssertion(
      final Consumer<UserTaskFilter> filter, final Consumer<List<UserTask>> assertion) {
    final AtomicReference<String> failureMessage = new AtomicReference<>("?");
    try {
      Awaitility.await()
          .ignoreException(ClientException.class)
          .untilAsserted(
              () -> dataSource.findUserTasks(filter),
              userTasks -> {
                try {
                  assertion.accept(userTasks);
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
