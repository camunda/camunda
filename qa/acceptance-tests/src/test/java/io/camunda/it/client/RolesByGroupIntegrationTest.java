/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.response.Role;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class RolesByGroupIntegrationTest {

  private static CamundaClient camundaClient;

  private static final String EXISTING_ROLE_ID = Strings.newRandomValidIdentityId();

  @BeforeAll
  static void setup() {
    createRole(EXISTING_ROLE_ID, "ARoleName", "description");

    Awaitility.await("Role is created and exported")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var role = camundaClient.newRoleGetRequest(EXISTING_ROLE_ID).send().join();
              assertThat(role).isNotNull();
              assertThat(role.getRoleId()).isEqualTo(EXISTING_ROLE_ID);
              assertThat(role.getName()).isEqualTo("ARoleName");
              assertThat(role.getDescription()).isEqualTo("description");
            });
  }

  @Test
  void shouldAssignRoleToGroup() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var groupName = UUID.randomUUID().toString();
    final var description = UUID.randomUUID().toString();
    createGroup(groupId, groupName, description);

    // when
    camundaClient
        .newAssignRoleToGroupCommand()
        .roleId(EXISTING_ROLE_ID)
        .groupId(groupId)
        .send()
        .join();

    // then
    assertRoleAssignedToGroup(EXISTING_ROLE_ID, groupId);
  }

  @Test
  void shouldRejectAssigningRoleToGroupIfRoleAlreadyAssigned() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var groupName = UUID.randomUUID().toString();
    final var description = UUID.randomUUID().toString();
    createGroup(groupId, groupName, description);

    camundaClient
        .newAssignRoleToGroupCommand()
        .roleId(EXISTING_ROLE_ID)
        .groupId(groupId)
        .send()
        .join();
    assertRoleAssignedToGroup(EXISTING_ROLE_ID, groupId);

    // when/then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToGroupCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .groupId(groupId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "Expected to add entity with ID '"
                + groupId
                + "' to role with ID '"
                + EXISTING_ROLE_ID
                + "', but the entity is already assigned to this role.");
  }

  @Test
  void shouldUnassignRoleFromGroupsOnRoleDeletion() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var roleName = UUID.randomUUID().toString();
    final var roleDescription = UUID.randomUUID().toString();
    final var groupId = Strings.newRandomValidIdentityId();
    final var groupName = UUID.randomUUID().toString();
    final var groupDescription = UUID.randomUUID().toString();
    createRole(roleId, roleName, roleDescription);
    createGroup(groupId, groupName, groupDescription);

    camundaClient.newAssignRoleToGroupCommand().roleId(roleId).groupId(groupId).send().join();
    assertRoleAssignedToGroup(roleId, groupId);

    // when
    camundaClient.newDeleteRoleCommand(roleId).send().join();

    // then
    Awaitility.await("Role was deleted and unassigned")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(() -> assertThat(searchRolesByGroupId(groupId).items()).isEmpty());
  }

  @Test
  void shouldUnassignRoleFromGroup() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var groupName = UUID.randomUUID().toString();
    final var description = UUID.randomUUID().toString();
    createGroup(groupId, groupName, description);

    camundaClient
        .newAssignRoleToGroupCommand()
        .roleId(EXISTING_ROLE_ID)
        .groupId(groupId)
        .send()
        .join();

    Awaitility.await("Group is assigned to the role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () ->
                assertThat(searchRolesByGroupId(groupId).items())
                    .anyMatch(r -> EXISTING_ROLE_ID.equals(r.getRoleId())));

    // when
    camundaClient
        .newUnassignRoleFromGroupCommand()
        .roleId(EXISTING_ROLE_ID)
        .groupId(groupId)
        .send()
        .join();

    // then
    Awaitility.await("Group is unassigned from the role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(() -> assertThat(searchRolesByGroupId(groupId).items()).isEmpty());
  }

  @Test
  void shouldRejectUnassigningIfRoleIsNotAssignedToGroup() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    camundaClient.newCreateGroupCommand().groupId(groupId).name("groupName").send().join();

    // when/then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromGroupCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .groupId(groupId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "Expected to remove entity with ID '"
                + groupId
                + "' from role with ID '"
                + EXISTING_ROLE_ID
                + "', but the entity is not assigned to this role.");
  }

  @Test
  void shouldAssigningRoleToGroupIfGroupDoesNotExist() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();

    // when
    camundaClient
        .newAssignRoleToGroupCommand()
        .roleId(EXISTING_ROLE_ID)
        .groupId(groupId)
        .send()
        .join();

    // then
    assertRoleAssignedToGroup(EXISTING_ROLE_ID, groupId);
  }

  @Test
  void shouldReturnNotFoundOnAssigningRoleToGroupIfRoleDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToGroupCommand()
                    .roleId(Strings.newRandomValidIdentityId())
                    .groupId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining("a role with this ID does not exist");
  }

  @Test
  void shouldRejectAssigningRoleToGroupIfMissingRoleId() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToGroupCommand()
                    .roleId(null)
                    .groupId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
  }

  @Test
  void shouldRejectAssigningRoleToGroupIfEmptyRoleId() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToGroupCommand()
                    .roleId("")
                    .groupId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be empty");
  }

  @Test
  void shouldRejectAssigningRoleToGroupIfMissingGroupId() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToGroupCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .groupId(null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be null");
  }

  @Test
  void shouldRejectAssigningRoleToGroupIfEmptyGroupId() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToGroupCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .groupId("")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be empty");
  }

  @Test
  void shouldRejectUnassigningRoleFromGroupIfMissingRoleId() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromGroupCommand()
                    .roleId(null)
                    .groupId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
  }

  @Test
  void shouldRejectUnassigningRoleFromGroupIfMissingGroupId() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromGroupCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .groupId(null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be null");
  }

  @Test
  void shouldRejectUnassigningRoleFromGroupIfRoleIdIsEmpty() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromGroupCommand()
                    .roleId("")
                    .groupId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be empty");
  }

  @Test
  void shouldRejectUnassigningRoleFromGroupIfGroupIdIsEmpty() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromGroupCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .groupId("")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be empty");
  }

  @Test
  void shouldUnassigningRoleFromGroupIfGroupDoesNotExist() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();

    camundaClient
        .newAssignRoleToGroupCommand()
        .roleId(EXISTING_ROLE_ID)
        .groupId(groupId)
        .send()
        .join();

    Awaitility.await("Group is assigned to the role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () ->
                assertThat(searchRolesByGroupId(groupId).items())
                    .anyMatch(r -> EXISTING_ROLE_ID.equals(r.getRoleId())));

    // when
    camundaClient
        .newUnassignRoleFromGroupCommand()
        .roleId(EXISTING_ROLE_ID)
        .groupId(groupId)
        .send()
        .join();

    // then
    Awaitility.await("Group is unassigned from the role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(() -> assertThat(searchRolesByGroupId(groupId).items()).isEmpty());
  }

  private static void createRole(
      final String roleId, final String roleName, final String description) {
    camundaClient
        .newCreateRoleCommand()
        .roleId(roleId)
        .name(roleName)
        .description(description)
        .send()
        .join();
  }

  private static void createGroup(
      final String groupId, final String groupName, final String description) {
    camundaClient
        .newCreateGroupCommand()
        .groupId(groupId)
        .name(groupName)
        .description(description)
        .send()
        .join();
  }

  private static void assertRoleAssignedToGroup(final String roleId, final String groupId) {
    Awaitility.await("Group is assigned to the role")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () ->
                assertThat(searchRolesByGroupId(groupId).items())
                    .anyMatch(r -> roleId.equals(r.getRoleId())));
  }

  private static SearchResponse<Role> searchRolesByGroupId(final String groupId) {
    return camundaClient.newRolesByGroupSearchRequest(groupId).send().join();
  }
}
