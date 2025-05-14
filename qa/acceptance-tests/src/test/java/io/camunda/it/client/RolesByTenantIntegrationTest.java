/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class RolesByTenantIntegrationTest {

  private static CamundaClient camundaClient;

  @Test
  void shouldAssignRoleToTenant() {
    // given
    final var tenantId = "tenant-" + Strings.newRandomValidIdentityId();
    final var roleId = "role-" + Strings.newRandomValidIdentityId();
    final var roleName = "name-" + Strings.newRandomValidIdentityId();
    final var roleDesc = "desc-" + Strings.newRandomValidIdentityId();
    createTenant(tenantId);
    createRole(roleId, roleName, roleDesc);
    // when
    camundaClient.newAssignRoleToTenantCommand(tenantId).roleId(roleId).send().join();
    // then
    Awaitility.await("Role should be visible in tenant role search")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var roles = camundaClient.newRolesByTenantSearchRequest(tenantId).send().join();
              assertThat(roles.items())
                  .singleElement()
                  .satisfies(
                      role -> {
                        assertThat(role.getRoleId()).isEqualTo(roleId);
                        assertThat(role.getName()).isEqualTo(roleName);
                        assertThat(role.getDescription()).isEqualTo(roleDesc);
                      });
            });
  }

  @Test
  void shouldRejectIfTenantDoesNotExist() {
    // given
    final var nonExistingTenantId = "tenant-" + Strings.newRandomValidIdentityId();
    final var roleId = "role-" + Strings.newRandomValidIdentityId();
    final var roleName = "name-" + Strings.newRandomValidIdentityId();
    final var roleDesc = "desc-" + Strings.newRandomValidIdentityId();
    createRole(roleId, roleName, roleDesc);
    // when and then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToTenantCommand(nonExistingTenantId)
                    .roleId(roleId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404: 'Not Found'")
        .hasMessageContaining("no tenant with this ID exists");
  }

  @Test
  void shouldRejectIfRoleDoesNotExist() {
    final var tenantId = "tenant-" + Strings.newRandomValidIdentityId();
    final var roleId = "role-" + Strings.newRandomValidIdentityId();
    createTenant(tenantId);
    assertThatThrownBy(
            () -> camundaClient.newAssignRoleToTenantCommand(tenantId).roleId(roleId).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404: 'Not Found'")
        .hasMessageContaining("role doesn't exist");
  }

  @Test
  void shouldRejectIfRoleAlreadyAssigned() {
    // given
    final var tenantId = "tenant-" + Strings.newRandomValidIdentityId();
    final var roleId = "role-" + Strings.newRandomValidIdentityId();
    final var roleName = "name-" + Strings.newRandomValidIdentityId();
    final var roleDesc = "desc-" + Strings.newRandomValidIdentityId();
    createTenant(tenantId);
    createRole(roleId, roleName, roleDesc);
    camundaClient.newAssignRoleToTenantCommand(tenantId).roleId(roleId).send().join();
    // when and then
    assertThatThrownBy(
            () -> camundaClient.newAssignRoleToTenantCommand(tenantId).roleId(roleId).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("409: 'Conflict'")
        .hasMessageContaining("the role is already assigned to the tenant");
  }

  @Test
  void shouldReturnAllRolesByTenant() {
    // given
    final var tenantId = "tenant-" + Strings.newRandomValidIdentityId();
    final var firstRoleId = "role-" + Strings.newRandomValidIdentityId();
    final var secondRoleId = "role-" + Strings.newRandomValidIdentityId();
    createTenant(tenantId);
    createRole(firstRoleId, "name1", "desc1");
    createRole(secondRoleId, "name2", "desc2");

    camundaClient.newAssignRoleToTenantCommand(tenantId).roleId(firstRoleId).send().join();
    camundaClient.newAssignRoleToTenantCommand(tenantId).roleId(secondRoleId).send().join();

    // when and then
    Awaitility.await("Roles should be visible in tenant role search")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var roles = camundaClient.newRolesByTenantSearchRequest(tenantId).send().join();
              assertThat(roles.items())
                  .extracting("roleId")
                  .containsExactlyInAnyOrder(firstRoleId, secondRoleId);
            });
  }

  @Test
  void shouldReturnEmptyListForTenantWithoutRoles() {
    final String emptyTenant = "empty-tenant-" + Strings.newRandomValidIdentityId();
    createTenant(emptyTenant);

    final var roles = camundaClient.newRolesByTenantSearchRequest(emptyTenant).send().join();

    assertThat(roles.items()).isEmpty();
  }

  @Test
  void shouldRejectSearchIfTenantIdIsNull() {
    assertThatThrownBy(() -> camundaClient.newRolesByTenantSearchRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be null");
  }

  @Test
  void shouldRejectSearchIfTenantIdIsEmpty() {
    assertThatThrownBy(() -> camundaClient.newRolesByTenantSearchRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be empty");
  }

  @Test
  void shouldUnassignRoleFromTenant() {
    // given
    final var tenantId = "tenant-" + Strings.newRandomValidIdentityId();
    final var roleId = "role-" + Strings.newRandomValidIdentityId();
    createTenant(tenantId);
    createRole(roleId, "name", "desc");
    camundaClient.newAssignRoleToTenantCommand(tenantId).roleId(roleId).send().join();
    // when
    camundaClient.newUnassignRoleFromTenantCommand(tenantId).roleId(roleId).send().join();
    // then
    final var roles = camundaClient.newRolesByTenantSearchRequest(tenantId).send().join();
    assertThat(roles.items()).noneMatch(role -> role.getRoleId().equals(roleId));
  }

  @Test
  void shouldRejectUnassignIfRoleNotAssignedToTenant() {
    // given
    final var tenantId = "tenant-" + Strings.newRandomValidIdentityId();
    final var roleId = "role-" + Strings.newRandomValidIdentityId();
    createTenant(tenantId);
    createRole(roleId, "name", "desc");

    // when and then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromTenantCommand(tenantId)
                    .roleId(roleId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404: 'Not Found'")
        .hasMessageContaining("the role is not assigned to this tenant");
  }

  @Test
  void shouldRejectUnassignWithNonExistingTenantId() {
    final var tenantId = "non-existing-" + Strings.newRandomValidIdentityId();
    final var roleId = "role-" + Strings.newRandomValidIdentityId();
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromTenantCommand(tenantId)
                    .roleId(roleId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404: 'Not Found'")
        .hasMessageContaining("no tenant with this ID exists");
  }

  @Test
  void shouldRejectUnassignWithNonExistingRoleId() {
    final var tenantId = "tenant-" + Strings.newRandomValidIdentityId();
    createTenant(tenantId);
    final var roleId = "non-existing-" + Strings.newRandomValidIdentityId();
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromTenantCommand(tenantId)
                    .roleId(roleId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404: 'Not Found'")
        .hasMessageContaining("the role is not assigned to this tenant");
  }

  private static void createTenant(final String tenantId) {
    camundaClient.newCreateTenantCommand().tenantId(tenantId).name("tenant name").send().join();
  }

  private static void createRole(final String roleId, final String name, final String description) {
    camundaClient
        .newCreateRoleCommand()
        .roleId(roleId)
        .name(name)
        .description(description)
        .send()
        .join();
  }
}
