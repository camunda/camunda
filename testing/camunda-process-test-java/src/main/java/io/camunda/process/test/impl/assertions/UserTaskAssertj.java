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

import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.process.test.api.CamundaAssertAwaitBehavior;
import io.camunda.process.test.api.assertions.UserTaskAssert;
import io.camunda.process.test.api.assertions.UserTaskSelector;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;

public class UserTaskAssertj extends AbstractAssert<UserTaskAssertj, UserTaskSelector>
    implements UserTaskAssert {

  private final CamundaDataSource dataSource;
  private final CamundaAssertAwaitBehavior awaitBehavior;

  public UserTaskAssertj(
      final CamundaDataSource dataSource,
      final CamundaAssertAwaitBehavior awaitBehavior,
      final UserTaskSelector selector) {
    super(selector, UserTaskAssert.class);
    this.dataSource = dataSource;
    this.awaitBehavior = awaitBehavior;
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
    awaitUserTask(
        userTask ->
            assertThat(userTask.getAssignee())
                .withFailMessage(
                    "Expected [%s] to have assignee '%s', but was '%s'",
                    actual.describe(), assignee, userTask.getAssignee())
                .isEqualTo(assignee));

    return this;
  }

  @Override
  public UserTaskAssert hasPriority(final int priority) {
    awaitUserTask(
        userTask ->
            assertThat(userTask.getPriority())
                .withFailMessage(
                    "Expected [%s] to have priority '%d', but was '%d'",
                    actual.describe(), priority, userTask.getPriority())
                .isEqualTo(priority));

    return this;
  }

  @Override
  public UserTaskAssert hasElementId(final String elementId) {
    awaitUserTask(
        userTask ->
            assertThat(userTask.getElementId().trim())
                .withFailMessage(
                    "Expected [%s] to have element ID '%s', but was '%s'",
                    actual.describe(), elementId, userTask.getElementId())
                .isEqualTo(elementId));

    return this;
  }

  @Override
  public UserTaskAssert hasName(final String name) {
    awaitUserTask(
        userTask ->
            assertThat(userTask.getName().trim())
                .withFailMessage(
                    "Expected [%s] to have name '%s', but was '%s'",
                    actual.describe(), name, userTask.getName())
                .isEqualTo(name));

    return this;
  }

  @Override
  public UserTaskAssert hasProcessInstanceKey(final long processInstanceKey) {
    awaitUserTask(
        userTask ->
            assertThat(userTask.getProcessInstanceKey())
                .withFailMessage(
                    "Expected [%s] to have processInstanceKey '%d', but was '%d'",
                    actual.describe(), processInstanceKey, userTask.getProcessInstanceKey())
                .isEqualTo(processInstanceKey));

    return this;
  }

  @Override
  public UserTaskAssert hasDueDate(final String dueDate) {
    awaitUserTask(
        userTask ->
            assertThat(userTask.getDueDate())
                .withFailMessage(
                    "Expected [%s] to have due date '%s', but was '%s'",
                    actual.describe(), dueDate, userTask.getDueDate())
                .isEqualTo(dueDate));

    return this;
  }

  @Override
  public UserTaskAssert hasCompletionDate(final String completionDate) {
    awaitUserTask(
        userTask ->
            assertThat(userTask.getCompletionDate())
                .withFailMessage(
                    "Expected [%s] to have completion date '%s', but was '%s'",
                    actual.describe(), completionDate, userTask.getCompletionDate())
                .isEqualTo(completionDate));

    return this;
  }

  @Override
  public UserTaskAssert hasFollowUpDate(final String followUpDate) {
    awaitUserTask(
        userTask ->
            assertThat(userTask.getFollowUpDate())
                .withFailMessage(
                    "Expected [%s] to have follow-up date '%s', but was '%s'",
                    actual.describe(), followUpDate, userTask.getFollowUpDate())
                .isEqualTo(followUpDate));

    return this;
  }

  @Override
  public UserTaskAssert hasCreationDate(final String creationDate) {
    awaitUserTask(
        userTask ->
            assertThat(userTask.getCreationDate())
                .withFailMessage(
                    "Expected [%s] to have creation date '%s', but was '%s'",
                    actual.describe(), creationDate, userTask.getCreationDate())
                .isEqualTo(creationDate));

    return this;
  }

  @Override
  public UserTaskAssert hasCandidateGroup(final String candidateGroup) {
    awaitUserTask(
        userTask ->
            assertThat(userTask.getCandidateGroups())
                .withFailMessage(
                    "Expected [%s] to have candidate group '%s', but was %s",
                    actual.describe(), candidateGroup, userTask.getCandidateGroups())
                .contains(candidateGroup));

    return this;
  }

  @Override
  public UserTaskAssert hasCandidateGroups(final List<String> candidateGroups) {
    awaitUserTask(
        userTask ->
            assertThat(userTask.getCandidateGroups())
                .withFailMessage(
                    "Expected [%s] to have candidate groups %s, but was %s",
                    actual.describe(), candidateGroups, userTask.getCandidateGroups())
                .containsAll(candidateGroups));

    return this;
  }

  private void hasUserTaskInState(final UserTaskState expectedState) {
    awaitUserTask(
        userTask ->
            assertThat(userTask.getState())
                .withFailMessage(
                    "Expected [%s] to be %s, but was %s",
                    actual.describe(), formatState(expectedState), formatState(userTask.getState()))
                .isEqualTo(expectedState));
  }

  private String formatState(final UserTaskState state) {
    if (state == null) {
      return "not activated";
    }

    return state.name().toLowerCase();
  }

  private void awaitUserTask(final Consumer<UserTask> assertion) {
    awaitBehavior.untilAsserted(
        () -> dataSource.findUserTasks(actual::applyFilter),
        userTasks -> {
          final Optional<UserTask> userTask = userTasks.stream().filter(actual::test).findFirst();

          assertThat(userTask)
              .withFailMessage("No user task [%s] found", actual.describe())
              .isPresent();

          assertion.accept(userTask.get());
        });
  }
}
