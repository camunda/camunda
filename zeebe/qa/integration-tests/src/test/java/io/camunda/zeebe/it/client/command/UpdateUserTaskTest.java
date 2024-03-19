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

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
@AutoCloseResources
class UpdateUserTaskTest {

  @TestZeebe(clusterSize = 1, partitionCount = 1, replicationFactor = 1)
  private static final TestCluster CLUSTER =
      TestCluster.builder().withEmbeddedGateway(false).withGatewaysCount(1).build();

  @AutoCloseResource private ZeebeClient client;

  private long userTaskKey;

  @BeforeEach
  void initClientAndInstances() {
    final var gateway = CLUSTER.availableGateway();
    client =
        CLUSTER
            .newClientBuilder()
            .restAddress(gateway.restAddress())
            .defaultRequestTimeout(Duration.ofSeconds(15))
            .build();
    final ZeebeResourcesHelper resourcesHelper = new ZeebeResourcesHelper(client);
    userTaskKey = resourcesHelper.createSingleUserTask();
  }

  @Test
  void shouldUpdateUserTaskWithAction() {
    // when
    client.newUserTaskUpdateCommand(userTaskKey).action("foo").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey, (userTask) -> assertThat(userTask.getAction()).isEqualTo("foo"));
  }

  @Test
  void shouldUpdateUserTaskWithDueDate() {
    // when
    client.newUserTaskUpdateCommand(userTaskKey).dueDate("myDate").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey, (userTask) -> assertThat(userTask.getDueDate()).isEqualTo("myDate"));
  }

  @Test
  void shouldUpdateUserTaskWithFollowUpDate() {
    // when
    client.newUserTaskUpdateCommand(userTaskKey).followUpDate("myDate").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey, (userTask) -> assertThat(userTask.getFollowUpDate()).isEqualTo("myDate"));
  }

  @Test
  void shouldUpdateUserTaskWithDueDateAndFollowUpDate() {
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
  void shouldUpdateUserTaskWithCandidateGroup() {
    // when
    client.newUserTaskUpdateCommand(userTaskKey).candidateGroups("foo").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> assertThat(userTask.getCandidateGroupsList()).containsOnly("foo"));
  }

  @Test
  void shouldUpdateUserTaskWithCandidateGroups() {
    // when
    client.newUserTaskUpdateCommand(userTaskKey).candidateGroups("foo", "bar").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> assertThat(userTask.getCandidateGroupsList()).containsOnly("foo", "bar"));
  }

  @Test
  void shouldUpdateUserTaskWithCandidateGroupsList() {
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
  void shouldUpdateUserTaskWithCandidateUser() {
    // when
    client.newUserTaskUpdateCommand(userTaskKey).candidateUsers("foo").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> assertThat(userTask.getCandidateUsersList()).containsOnly("foo"));
  }

  @Test
  void shouldUpdateUserTaskWithCandidateUsers() {
    // when
    client.newUserTaskUpdateCommand(userTaskKey).candidateUsers("foo", "bar").send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> assertThat(userTask.getCandidateUsersList()).containsOnly("foo", "bar"));
  }

  @Test
  void shouldUpdateUserTaskWithCandidateUsersList() {
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
  void shouldClearUserTaskDueDate() {
    // given
    client.newUserTaskUpdateCommand(userTaskKey).dueDate("foo").send().join();

    // when
    client.newUserTaskUpdateCommand(userTaskKey).clearDueDate().send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey, 2L, (userTask) -> assertThat(userTask.getDueDate()).isEmpty());
  }

  @Test
  void shouldClearUserTaskFollowUpDate() {
    // given
    client.newUserTaskUpdateCommand(userTaskKey).followUpDate("foo").send().join();

    // when
    client.newUserTaskUpdateCommand(userTaskKey).clearFollowUpDate().send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey, 2L, (userTask) -> assertThat(userTask.getFollowUpDate()).isEmpty());
  }

  @Test
  void shouldClearUserTaskCandidateGroups() {
    // given
    client.newUserTaskUpdateCommand(userTaskKey).candidateGroups("foo").send().join();

    // when
    client.newUserTaskUpdateCommand(userTaskKey).clearCandidateGroups().send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey, 2L, (userTask) -> assertThat(userTask.getCandidateGroupsList()).isEmpty());
  }

  @Test
  void shouldClearUserTaskCandidateUsers() {
    // given
    client.newUserTaskUpdateCommand(userTaskKey).candidateUsers("foo").send().join();

    // when
    client.newUserTaskUpdateCommand(userTaskKey).clearCandidateUsers().send().join();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey, 2L, (userTask) -> assertThat(userTask.getCandidateUsersList()).isEmpty());
  }

  @Test
  void shouldRejectIfMissingUpdateData() {
    // when / then
    assertThatThrownBy(() -> client.newUserTaskUpdateCommand(userTaskKey).send().join())
        .hasCauseInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 400: 'Bad Request'");
  }
}
