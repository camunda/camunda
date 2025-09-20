/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class UpdateUserTaskTest {

  private static final OffsetDateTime TEST_DATE_TIME =
      OffsetDateTime.of(2023, 11, 11, 11, 11, 11, 11, ZoneOffset.of("Z"));

  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose private CamundaClient client;

  private long userTaskKey;

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    final ZeebeResourcesHelper resourcesHelper = new ZeebeResourcesHelper(client);
    userTaskKey = resourcesHelper.createSingleUserTask();
  }

  @Test
  void shouldUpdateUserTaskWithAction() {
    // when
    client.newUpdateUserTaskCommand(userTaskKey).action("foo").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey, (userTask) -> assertThat(userTask.getAction()).isEqualTo("foo"));
  }

  @Test
  void shouldUpdateUserTaskWithDueDate() {
    // when
    client.newUpdateUserTaskCommand(userTaskKey).dueDate(TEST_DATE_TIME).send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> assertThat(userTask.getDueDate()).isEqualTo(TEST_DATE_TIME.toString()));
  }

  @Test
  void shouldUpdateUserTaskWithFollowUpDate() {
    // when
    client.newUpdateUserTaskCommand(userTaskKey).followUpDate(TEST_DATE_TIME).send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> assertThat(userTask.getFollowUpDate()).isEqualTo(TEST_DATE_TIME.toString()));
  }

  @Test
  void shouldUpdateUserTaskWithDueDateAndFollowUpDate() {
    // when
    client
        .newUpdateUserTaskCommand(userTaskKey)
        .dueDate(TEST_DATE_TIME)
        .followUpDate(TEST_DATE_TIME.plusDays(1))
        .send()
        .join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> {
          assertThat(userTask.getDueDate()).isEqualTo(TEST_DATE_TIME.toString());
          assertThat(userTask.getFollowUpDate()).isEqualTo(TEST_DATE_TIME.plusDays(1).toString());
        });
  }

  @Test
  void shouldUpdateUserTaskWithCandidateGroup() {
    // when
    client.newUpdateUserTaskCommand(userTaskKey).candidateGroups("foo").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> assertThat(userTask.getCandidateGroupsList()).containsOnly("foo"));
  }

  @Test
  void shouldUpdateUserTaskWithCandidateGroups() {
    // when
    client.newUpdateUserTaskCommand(userTaskKey).candidateGroups("foo", "bar").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> assertThat(userTask.getCandidateGroupsList()).containsOnly("foo", "bar"));
  }

  @Test
  void shouldUpdateUserTaskWithCandidateGroupsList() {
    // when
    client
        .newUpdateUserTaskCommand(userTaskKey)
        .candidateGroups(List.of("foo", "bar"))
        .send()
        .join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> assertThat(userTask.getCandidateGroupsList()).containsOnly("foo", "bar"));
  }

  @Test
  void shouldUpdateUserTaskWithCandidateUser() {
    // when
    client.newUpdateUserTaskCommand(userTaskKey).candidateUsers("foo").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> assertThat(userTask.getCandidateUsersList()).containsOnly("foo"));
  }

  @Test
  void shouldUpdateUserTaskWithCandidateUsers() {
    // when
    client.newUpdateUserTaskCommand(userTaskKey).candidateUsers("foo", "bar").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> assertThat(userTask.getCandidateUsersList()).containsOnly("foo", "bar"));
  }

  @Test
  void shouldUpdateUserTaskWithCandidateUsersList() {
    // when
    client
        .newUpdateUserTaskCommand(userTaskKey)
        .candidateUsers(List.of("foo", "bar"))
        .send()
        .join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> assertThat(userTask.getCandidateUsersList()).containsOnly("foo", "bar"));
  }

  @Test
  void shouldClearUserTaskDueDate() {
    // given
    client.newUpdateUserTaskCommand(userTaskKey).dueDate(TEST_DATE_TIME).send().join();

    // when
    client.newUpdateUserTaskCommand(userTaskKey).clearDueDate().send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey, 2L, (userTask) -> assertThat(userTask.getDueDate()).isEmpty());
  }

  @Test
  void shouldClearUserTaskFollowUpDate() {
    // given
    client.newUpdateUserTaskCommand(userTaskKey).followUpDate(TEST_DATE_TIME).send().join();

    // when
    client.newUpdateUserTaskCommand(userTaskKey).clearFollowUpDate().send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey, 2L, (userTask) -> assertThat(userTask.getFollowUpDate()).isEmpty());
  }

  @Test
  void shouldClearUserTaskCandidateGroups() {
    // given
    client.newUpdateUserTaskCommand(userTaskKey).candidateGroups("foo").send().join();

    // when
    client.newUpdateUserTaskCommand(userTaskKey).clearCandidateGroups().send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey, 2L, (userTask) -> assertThat(userTask.getCandidateGroupsList()).isEmpty());
  }

  @Test
  void shouldClearUserTaskCandidateUsers() {
    // given
    client.newUpdateUserTaskCommand(userTaskKey).candidateUsers("foo").send().join();

    // when
    client.newUpdateUserTaskCommand(userTaskKey).clearCandidateUsers().send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey, 2L, (userTask) -> assertThat(userTask.getCandidateUsersList()).isEmpty());
  }

  @Test
  void shouldRejectIfMissingUpdateData() {
    // when / then
    assertThatThrownBy(() -> client.newUpdateUserTaskCommand(userTaskKey).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 400: 'Bad Request'");
  }
}
