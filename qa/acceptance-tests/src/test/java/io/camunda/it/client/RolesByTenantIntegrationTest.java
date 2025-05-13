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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class RolesByTenantIntegrationTest {

  private static CamundaClient camundaClient;

  private static final String TENANT_ID = "tenant-" + Strings.newRandomValidIdentityId();
  private static final String ROLE_ID_1 = "role-" + Strings.newRandomValidIdentityId();
  private static final String ROLE_ID_1_NAME = "name-" + Strings.newRandomValidIdentityId();
  private static final String ROLE_ID_1_DESC = "desc-" + Strings.newRandomValidIdentityId();
  private static final String ROLE_ID_2 = "role-" + Strings.newRandomValidIdentityId();
  private static final String NON_EXISTING_ROLE_ID = "non-existent-role";
  private static final String NON_EXISTING_TENANT_ID = "non-existent-tenant";

  @BeforeAll
  static void setup() {
    createTenant(TENANT_ID);
  }

  @AfterEach
  void cleanupRoles() {
    try {
      camundaClient.newDeleteRoleCommand(ROLE_ID_1).send().join();
    } catch (final Exception ignored) {
      // ignore
    }
    try {
      camundaClient.newDeleteRoleCommand(ROLE_ID_2).send().join();
    } catch (final Exception ignored) {
      // ignore
    }

    Awaitility.await("Roles should be deleted")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var roles =
                  camundaClient.newRolesByTenantSearchRequest(TENANT_ID).send().join();
              assertThat(roles.items()).isEmpty();
            });
  }

  @Test
  void shouldAssignRoleToTenant() {
    // given
    createRole(ROLE_ID_1, ROLE_ID_1_NAME, ROLE_ID_1_DESC);
    // when
    camundaClient.newAssignRoleToTenantCommand(TENANT_ID).roleId(ROLE_ID_1).send().join();
    // then
    Awaitility.await("Role should be visible in tenant role search")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var roles =
                  camundaClient.newRolesByTenantSearchRequest(TENANT_ID).send().join();
              assertThat(roles.items())
                  .anySatisfy(
                      role -> {
                        assertThat(role.getRoleId()).isEqualTo(ROLE_ID_1);
                        assertThat(role.getName()).isEqualTo(ROLE_ID_1_NAME);
                        assertThat(role.getDescription()).isEqualTo(ROLE_ID_1_DESC);
                      });
            });
  }

  @Test
  void shouldRejectIfTenantDoesNotExist() {
    // given
    createRole(ROLE_ID_1, ROLE_ID_1_NAME, ROLE_ID_1_DESC);
    // when and then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToTenantCommand(NON_EXISTING_TENANT_ID)
                    .roleId(ROLE_ID_1)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404");
  }

  @Test
  void shouldRejectIfRoleDoesNotExist() {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToTenantCommand(TENANT_ID)
                    .roleId(NON_EXISTING_ROLE_ID)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404");
  }

  @Test
  void shouldRejectIfRoleAlreadyAssigned() {
    // given
    createRole(ROLE_ID_1, ROLE_ID_1_NAME, ROLE_ID_1_DESC);
    camundaClient.newAssignRoleToTenantCommand(TENANT_ID).roleId(ROLE_ID_1).send().join();
    // when and then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newAssignRoleToTenantCommand(TENANT_ID)
                    .roleId(ROLE_ID_1)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("409");
  }

  @Test
  void shouldReturnAllRolesByTenant() {
    // given
    createRole(ROLE_ID_1, ROLE_ID_1_NAME, ROLE_ID_1_DESC);
    createRole(ROLE_ID_2, "roleName2", "description2");

    camundaClient.newAssignRoleToTenantCommand(TENANT_ID).roleId(ROLE_ID_1).send().join();
    camundaClient.newAssignRoleToTenantCommand(TENANT_ID).roleId(ROLE_ID_2).send().join();

    // when and then
    Awaitility.await("Roles should be visible in tenant role search")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var roles =
                  camundaClient.newRolesByTenantSearchRequest(TENANT_ID).send().join();
              assertThat(roles.items())
                  .extracting("roleId")
                  .containsExactlyInAnyOrder(ROLE_ID_1, ROLE_ID_2);
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
    createRole(ROLE_ID_1, ROLE_ID_1_NAME, ROLE_ID_1_DESC);
    camundaClient.newAssignRoleToTenantCommand(TENANT_ID).roleId(ROLE_ID_1).send().join();

    Awaitility.await("Role should be visible before unassign")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var roles =
                  camundaClient.newRolesByTenantSearchRequest(TENANT_ID).send().join();
              assertThat(roles.items()).anyMatch(role -> role.getRoleId().equals(ROLE_ID_1));
            });
    // when
    camundaClient.newUnassignRoleFromTenantCommand(TENANT_ID).roleId(ROLE_ID_1).send().join();
    // then
    Awaitility.await("Role should be removed from tenant")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var roles =
                  camundaClient.newRolesByTenantSearchRequest(TENANT_ID).send().join();
              assertThat(roles.items()).noneMatch(role -> role.getRoleId().equals(ROLE_ID_1));
            });
  }

  @Test
  void shouldRejectUnassignIfRoleNotAssignedToTenant() {
    // given
    createRole(ROLE_ID_1, ROLE_ID_1_NAME, ROLE_ID_1_DESC);

    // when and then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUnassignRoleFromTenantCommand(TENANT_ID)
                    .roleId(ROLE_ID_1)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("404");
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
