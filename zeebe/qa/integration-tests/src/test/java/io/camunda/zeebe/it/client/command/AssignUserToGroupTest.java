/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class AssignUserToGroupTest {

  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose private CamundaClient client;

  private String groupId;
  private String username;

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    username =
        client
            .newUserCreateCommand()
            .name("User Name")
            .username(Strings.newRandomValidUsername())
            .email("foo@example.com")
            .password("******")
            .send()
            .join()
            .getUsername();

    groupId =
        client
            .newCreateGroupCommand()
            .groupId(Strings.newRandomValidIdentityId())
            .name("groupName")
            .send()
            .join()
            .getGroupId();
  }

  @Test
  void shouldAssignUserToGroup() {
    // when
    client.newAssignUserToGroupCommand(groupId).username(username).send().join();

    // then
    ZeebeAssertHelper.assertEntityAssignedToGroup(
        groupId,
        username,
        group -> {
          assertThat(group).hasEntityType(EntityType.USER);
        });
  }

  @Test
  void shouldRejectIfGroupDoesNotExist() {
    // given
    final String nonExistentGroupId = Strings.newRandomValidIdentityId();

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignUserToGroupCommand(nonExistentGroupId)
                    .username(username)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to update group with ID '%s', but a group with this ID does not exist."
                .formatted(nonExistentGroupId));
  }

  @Test
  void shouldRejectIfAlreadyAssigned() {
    // given
    client.newAssignUserToGroupCommand(groupId).username(username).send().join();

    // when / then
    assertThatThrownBy(
            () -> client.newAssignUserToGroupCommand(groupId).username(username).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'")
        .hasMessageContaining(
            "Expected to add entity with ID '%s' to group with ID '%s', but the entity is already assigned to this group."
                .formatted(username, groupId));
  }

  @Test
  void shouldRejectIfMissingGroupId() {
    // when / then
    assertThatThrownBy(
            () -> client.newAssignUserToGroupCommand(null).username("username").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be null");
  }

  @Test
  void shouldRejectIfEmptyGroupId() {
    // when / then
    assertThatThrownBy(
            () -> client.newAssignUserToGroupCommand("").username("username").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be empty");
  }

  @Test
  void shouldRejectIfMissingUsername() {
    // when / then
    assertThatThrownBy(
            () -> client.newAssignUserToGroupCommand("groupId").username(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("username must not be null");
  }

  @Test
  void shouldRejectIfEmptyUsername() {
    // when / then
    assertThatThrownBy(
            () -> client.newAssignUserToGroupCommand("groupId").username("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("username must not be empty");
  }
}
