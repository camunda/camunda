/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Role;
import io.camunda.client.api.search.response.RoleUser;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class RoleTenancyIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthenticatedAccess();

  private static final String ADMIN = "admin";
  private static final String USER1 = "user1";
  private static final String TENANT_A = "tenantA";
  private static final String TENANT_B = "tenantB";
  private static final String ROLE_A = "roleA";
  private static final String ROLE_B = "roleB";

  @UserDefinition
  private static final TestUser ADMIN_USER = new TestUser(ADMIN, "password", List.of());

  @UserDefinition
  private static final TestUser USER1_USER = new TestUser(USER1, "password", List.of());

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    createTenant(adminClient, TENANT_A);
    createTenant(adminClient, TENANT_B);
    assignUserToTenant(adminClient, ADMIN, TENANT_A);
    assignUserToTenant(adminClient, ADMIN, TENANT_B);
    createRole(adminClient, ROLE_A);
    createRole(adminClient, ROLE_B);
    assignUserToRole(adminClient, ADMIN, ROLE_A);
    waitForRolesBeingExported(adminClient);
    waitForRoleMembershipBeingExported(adminClient);
  }

  @Test
  public void shouldReturnAllRolesWithTenantAccess(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {
    // when
    final var result =
        camundaClient.newRolesSearchRequest().filter(f -> f.roleId(ROLE_A)).send().join();
    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().stream().map(Role::getRoleId).toList())
        .containsExactlyInAnyOrder(ROLE_A);
  }

  @Test
  public void shouldReturnRoleMembershipsWithTenantAccess(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newUsersByRoleSearchRequest(ROLE_A).send().join();
    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().stream().map(RoleUser::getUsername).toList())
        .containsExactlyInAnyOrder(ADMIN);
  }

  @Test
  public void shouldReturnAllRolesWithNoTenantAccess(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final var result =
        camundaClient.newRolesSearchRequest().filter(f -> f.roleId(ROLE_B)).send().join();
    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().stream().map(Role::getRoleId).toList())
        .containsExactlyInAnyOrder(ROLE_B);
  }

  @Test
  public void shouldReturnRoleMembershipsWithNoTenantAccess(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newUsersByRoleSearchRequest(ROLE_A).send().join();
    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().stream().map(RoleUser::getUsername).toList())
        .containsExactlyInAnyOrder(ADMIN);
  }

  private static void createRole(final CamundaClient camundaClient, final String role) {
    camundaClient.newCreateRoleCommand().roleId(role).name(role).send().join();
  }

  private static void createTenant(final CamundaClient camundaClient, final String tenant) {
    camundaClient.newCreateTenantCommand().tenantId(tenant).name(tenant).send().join();
  }

  private static void assignUserToTenant(
      final CamundaClient camundaClient, final String username, final String tenant) {
    camundaClient.newAssignUserToTenantCommand().username(username).tenantId(tenant).send().join();
  }

  private static void assignUserToRole(
      final CamundaClient camundaClient, final String username, final String role) {
    camundaClient.newAssignRoleToUserCommand().roleId(role).username(username).send().join();
  }

  private static void waitForRolesBeingExported(final CamundaClient camundaClient) {
    Awaitility.await("should receive data from secondary storage")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              assertThat(
                      camundaClient
                          .newRolesSearchRequest()
                          .filter(f -> f.roleId(ROLE_B))
                          .send()
                          .join()
                          .items())
                  .hasSize(1);
            });
  }

  private static void waitForRoleMembershipBeingExported(final CamundaClient camundaClient) {
    Awaitility.await("should receive data from secondary storage")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              assertThat(camundaClient.newUsersByRoleSearchRequest(ROLE_A).send().join().items())
                  .hasSize(1);
            });
  }
}
