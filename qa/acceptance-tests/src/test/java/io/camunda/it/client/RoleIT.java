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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.filter.AuthorizationFilter;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.util.UUID;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class RoleIT {

  private static CamundaClient camundaClient;

  private static final String EXISTING_ROLE_ID = Strings.newRandomValidIdentityId();
  private static final String EXISTING_ROLE_NAME = "ARoleName";
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @BeforeAll
  static void setup() {
    createRole(EXISTING_ROLE_ID, EXISTING_ROLE_NAME, "description");
    assertRoleCreated(EXISTING_ROLE_ID, EXISTING_ROLE_NAME, "description");
  }

  @Test
  void shouldCreateAndGetRoleById() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var roleName = UUID.randomUUID().toString();
    final var description = UUID.randomUUID().toString();

    // when
    createRole(roleId, roleName, description);
    // then
    assertRoleCreated(roleId, roleName, description);
  }

  @Test
  void shouldRejectCreationIfRoleIdAlreadyExists() {
    // when/then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newCreateRoleCommand()
                    .roleId(EXISTING_ROLE_ID)
                    .name(EXISTING_ROLE_NAME)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'")
        .hasMessageContaining("a role with this ID already exists");
  }

  @Test
  void shouldRejectCreationIfMissingRoleId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newRoleGetRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
  }

  @Test
  void shouldRejectCreationIfEmptyRoleId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newRoleGetRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be empty");
  }

  @Test
  void shouldRejectCreationIfMissingRoleName() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newCreateRoleCommand()
                    .roleId(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be null");
  }

  @Test
  void shouldReturnNotFoundWhenGetRoleDoesNotExist() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newRoleGetRequest("someRoleId").send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining("Role with id 'someRoleId' not found");
  }

  @Test
  void shouldRejectGetRoleIfNullRoleId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newRoleGetRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
  }

  @Test
  void shouldRejectGetRoleIfEmptyRoleId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newRoleGetRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be empty");
  }

  @Test
  void shouldDeleteRoleById() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var roleName = UUID.randomUUID().toString();
    final var description = UUID.randomUUID().toString();

    // given
    createRole(roleId, roleName, description);

    assertRoleCreated(roleId, roleName, description);

    // when
    camundaClient.newDeleteRoleCommand(roleId).send().join();

    // then
    Awaitility.await("Role is deleted")
        .untilAsserted(
            () ->
                assertThatThrownBy(() -> camundaClient.newRoleGetRequest(roleId).send().join())
                    .isInstanceOf(ProblemException.class)
                    .hasMessageContaining("Failed with code 404: 'Not Found'"));
  }

  @Test
  void shouldReturnNotFoundOnDeleteWhenRoleDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newDeleteRoleCommand(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining("a role with this ID doesn't exist");
  }

  @Test
  void shouldRejectDeletionIfNullRoleId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newDeleteRoleCommand(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
  }

  @Test
  void shouldRejectDeletionIfEmptyRoleId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newDeleteRoleCommand("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be empty");
  }

  @Test
  void shouldDeleteAuthorizationsWhenDeletingRole() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var roleName = UUID.randomUUID().toString();
    final var description = UUID.randomUUID().toString();

    // when
    createRole(roleId, roleName, description);

    assertRoleCreated(roleId, roleName, description);

    camundaClient
        .newCreateAuthorizationCommand()
        .ownerId(roleId)
        .ownerType(OwnerType.ROLE)
        .resourceId("resourceId")
        .resourceType(ResourceType.RESOURCE)
        .permissionTypes(PermissionType.CREATE, PermissionType.READ)
        .send()
        .join();

    final Consumer<AuthorizationFilter> createdAuthorizationFilter =
        af ->
            af.resourceIds("resourceId")
                .resourceType(ResourceType.RESOURCE)
                .ownerType(OwnerType.ROLE)
                .ownerId(roleId);

    Awaitility.await("Authorization was created")
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newAuthorizationSearchRequest()
                            .filter(createdAuthorizationFilter)
                            .execute()
                            .items())
                    .isNotEmpty());

    camundaClient.newDeleteRoleCommand(roleId).send().join();

    // then
    Awaitility.await("Role is deleted")
        .untilAsserted(
            () ->
                assertThatThrownBy(() -> camundaClient.newRoleGetRequest(roleId).send().join())
                    .isInstanceOf(ProblemException.class)
                    .hasMessageContaining("Failed with code 404: 'Not Found'"));

    Awaitility.await("Authorization is deleted")
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newAuthorizationSearchRequest()
                            .filter(createdAuthorizationFilter)
                            .execute()
                            .items())
                    .isEmpty());
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

  @Test
  void shouldUpdateRole() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var roleName = UUID.randomUUID().toString();
    final var description = UUID.randomUUID().toString();
    final var updatedName = UUID.randomUUID().toString();
    final var updatedDescription = UUID.randomUUID().toString();
    camundaClient
        .newCreateRoleCommand()
        .roleId(roleId)
        .name(roleName)
        .description(description)
        .send()
        .join();

    // when
    camundaClient
        .newUpdateRoleCommand(roleId)
        .name(updatedName)
        .description(updatedDescription)
        .send()
        .join();

    // then
    Awaitility.await("Role is updated")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var role = camundaClient.newRoleGetRequest(roleId).send().join();
              assertThat(role).isNotNull();
              assertThat(role.getRoleId()).isEqualTo(roleId);
              assertThat(role.getName()).isEqualTo(updatedName);
              assertThat(role.getDescription()).isEqualTo(updatedDescription);
            });
  }

  @Test
  void shouldReturnNotFoundWhenUpdatingIfRoleDoesNotExist() {
    // when / then
    final var nonExistingRoleId = Strings.newRandomValidIdentityId();
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUpdateRoleCommand(nonExistingRoleId)
                    .name("Role Name")
                    .description("Description")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to update role with ID '%s', but a role with this ID does not exist."
                .formatted(nonExistingRoleId));
  }

  private static void assertRoleCreated(
      final String roleId, final String roleName, final String description) {
    Awaitility.await("Role is created and exported")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var role = camundaClient.newRoleGetRequest(roleId).send().join();
              assertThat(role).isNotNull();
              assertThat(role.getRoleId()).isEqualTo(roleId);
              assertThat(role.getName()).isEqualTo(roleName);
              assertThat(role.getDescription()).isEqualTo(description);
            });
  }
}
