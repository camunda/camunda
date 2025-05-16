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
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class RolesByClientIntegrationTest {
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
              assertThat(role.getRoleKey()).isPositive();
            });
  }

  @Test
  void shouldAssignRoleToClient() {
    // TODO: client search is currently in implementation
  }

  @Test
  void shouldUnassignRoleFromClient() {
    // TODO: client search is currently in implementation
  }

  @Test
  void shouldUnassignRoleFromClientOnRoleDeletion() {
    // TODO: client search is currently in implementation
  }

  @Test
  void shouldRejectAssigningRoleIfRoleAlreadyAssignedToClient() {
    // given
    final var clientId = Strings.newRandomValidIdentityId();

    camundaClient
        .newAssignRoleToClientCommand()
        .roleId(EXISTING_ROLE_ID)
        .clientId(clientId)
        .send()
        .join();

    // when/then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToClientCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .clientId(clientId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "Expected to add entity with ID '"
                + clientId
                + "' to role with ID '"
                + EXISTING_ROLE_ID
                + "', but the entity is already assigned to this role.");
  }

  @Test
  void shouldRejectUnassigningRoleIfRoleNotAssignedToClient() {
    // given
    final var clientId = Strings.newRandomValidIdentityId();

    // when/then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromClientCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .clientId(clientId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "Expected to remove entity with ID '"
                + clientId
                + "' from role with ID '"
                + EXISTING_ROLE_ID
                + "', but the entity is not assigned to this role.");
  }

  @Test
  void shouldReturnNotFoundOnAssigningRoleToClientIfRoleDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToClientCommand()
                    .roleId(Strings.newRandomValidIdentityId())
                    .clientId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining("a role with this ID does not exist");
  }

  @Test
  void shouldReturnNotFoundOnUnassigningRoleFromClientIfRoleDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromClientCommand()
                    .roleId(Strings.newRandomValidIdentityId())
                    .clientId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining("a role with this ID does not exist");
  }

  @Test
  void shouldRejectAssigningRoleToClientIfMissingClientId() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToClientCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .clientId(null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("clientId must not be null");
  }

  @Test
  void shouldRejectUnassigningRoleFromClientIfMissingClientId() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromClientCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .clientId(null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("clientId must not be null");
  }

  @Test
  void shouldRejectAssigningRoleToClientIfMissingRoleId() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToClientCommand()
                    .roleId(null)
                    .clientId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
  }

  @Test
  void shouldRejectUnassigningRolefromClientIfMissingRoleId() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromClientCommand()
                    .roleId(null)
                    .clientId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
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
}
