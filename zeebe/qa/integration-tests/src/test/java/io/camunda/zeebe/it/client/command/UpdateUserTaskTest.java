/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class UpdateUserTaskTest extends UserTaskTest {

  @Test
  public void shouldUpdateUserTaskWithAction() {
    // when
    client.newUserTaskUpdateCommand(userTaskKey).action("foo").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey, (userTask) -> assertThat(userTask.getAction()).isEqualTo("foo"));
  }

  @Test
  public void shouldUpdateUserTaskWithDueDate() {
    // when
    client.newUserTaskUpdateCommand(userTaskKey).dueDate("myDate").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey, (userTask) -> assertThat(userTask.getDueDate()).isEqualTo("myDate"));
  }

  @Test
  public void shouldUpdateUserTaskWithFollowUpDate() {
    // when
    client.newUserTaskUpdateCommand(userTaskKey).followUpDate("myDate").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey, (userTask) -> assertThat(userTask.getFollowUpDate()).isEqualTo("myDate"));
  }

  @Test
  public void shouldUpdateUserTaskWithDueDateAndFollowUpDate() {
    // when
    client
        .newUserTaskUpdateCommand(userTaskKey)
        .dueDate("myDate")
        .followUpDate("myOtherDate")
        .send()
        .join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> {
          assertThat(userTask.getDueDate()).isEqualTo("myDate");
          assertThat(userTask.getFollowUpDate()).isEqualTo("myOtherDate");
        });
  }

  @Test
  public void shouldUpdateUserTaskWithCandidateGroup() {
    // when
    client.newUserTaskUpdateCommand(userTaskKey).candidateGroups("foo").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> assertThat(userTask.getCandidateGroupsList()).containsOnly("foo"));
  }

  @Test
  public void shouldUpdateUserTaskWithCandidateGroups() {
    // when
    client.newUserTaskUpdateCommand(userTaskKey).candidateGroups("foo", "bar").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> assertThat(userTask.getCandidateGroupsList()).containsOnly("foo", "bar"));
  }

  @Test
  public void shouldUpdateUserTaskWithCandidateGroupsList() {
    // when
    client
        .newUserTaskUpdateCommand(userTaskKey)
        .candidateGroups(List.of("foo", "bar"))
        .send()
        .join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> assertThat(userTask.getCandidateGroupsList()).containsOnly("foo", "bar"));
  }

  @Test
  public void shouldUpdateUserTaskWithCandidateUser() {
    // when
    client.newUserTaskUpdateCommand(userTaskKey).candidateUsers("foo").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> assertThat(userTask.getCandidateUsersList()).containsOnly("foo"));
  }

  @Test
  public void shouldUpdateUserTaskWithCandidateUsers() {
    // when
    client.newUserTaskUpdateCommand(userTaskKey).candidateUsers("foo", "bar").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> assertThat(userTask.getCandidateUsersList()).containsOnly("foo", "bar"));
  }

  @Test
  public void shouldUpdateUserTaskWithCandidateUsersList() {
    // when
    client
        .newUserTaskUpdateCommand(userTaskKey)
        .candidateUsers(List.of("foo", "bar"))
        .send()
        .join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> assertThat(userTask.getCandidateUsersList()).containsOnly("foo", "bar"));
  }

  @Test
  public void shouldClearUserTaskDueDate() {
    // given
    client.newUserTaskUpdateCommand(userTaskKey).dueDate("foo").send().join();

    // when
    client.newUserTaskUpdateCommand(userTaskKey).clearDueDate().send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey, 2L, (userTask) -> assertThat(userTask.getDueDate()).isEmpty());
  }

  @Test
  public void shouldClearUserTaskFollowUpDate() {
    // given
    client.newUserTaskUpdateCommand(userTaskKey).followUpDate("foo").send().join();

    // when
    client.newUserTaskUpdateCommand(userTaskKey).clearFollowUpDate().send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey, 2L, (userTask) -> assertThat(userTask.getFollowUpDate()).isEmpty());
  }

  @Test
  public void shouldClearUserTaskCandidateGroups() {
    // given
    client.newUserTaskUpdateCommand(userTaskKey).candidateGroups("foo").send().join();

    // when
    client.newUserTaskUpdateCommand(userTaskKey).clearCandidateGroups().send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey, 2L, (userTask) -> assertThat(userTask.getCandidateGroupsList()).isEmpty());
  }

  @Test
  public void shouldClearUserTaskCandidateUsers() {
    // given
    client.newUserTaskUpdateCommand(userTaskKey).candidateUsers("foo").send().join();

    // when
    client.newUserTaskUpdateCommand(userTaskKey).clearCandidateUsers().send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey, 2L, (userTask) -> assertThat(userTask.getCandidateUsersList()).isEmpty());
  }

  @Test
  public void shouldRejectIfMissingUpdateData() {
    // when / then
    assertThatThrownBy(() -> client.newUserTaskUpdateCommand(userTaskKey).send().join())
        .hasCauseInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 400: 'Bad Request'");
  }
}
