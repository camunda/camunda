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
import io.camunda.client.api.search.response.Group;
import io.camunda.client.api.search.response.GroupUser;
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
public class GroupTenancyIT {

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
  private static final String GROUP_A = "groupA";
  private static final String GROUP_B = "groupB";

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
    createGroup(adminClient, GROUP_A);
    createGroup(adminClient, GROUP_B);
    assignUserToGroup(adminClient, ADMIN, GROUP_A);
    waitForGroupsBeingExported(adminClient);
    waitForGroupMembershipsBeingExported(adminClient);
  }

  @Test
  public void shouldReturnAllGroupsWithTenantAccess(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newGroupsSearchRequest().send().join();
    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().stream().map(Group::getGroupId).toList())
        .containsExactlyInAnyOrder(GROUP_A, GROUP_B);
  }

  @Test
  public void shouldReturnGroupMembershipsWithTenantAccess(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newUsersByGroupSearchRequest(GROUP_A).send().join();
    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().stream().map(GroupUser::getUsername).toList())
        .containsExactlyInAnyOrder(ADMIN);
  }

  @Test
  public void shouldReturnAllGroupsWithNoTenantAccess(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newGroupsSearchRequest().send().join();
    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().stream().map(Group::getGroupId).toList())
        .containsExactlyInAnyOrder(GROUP_A, GROUP_B);
  }

  @Test
  public void shouldReturnGroupMembershipsWithNoTenantAccess(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newUsersByGroupSearchRequest(GROUP_A).send().join();
    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().stream().map(GroupUser::getUsername).toList())
        .containsExactlyInAnyOrder(ADMIN);
  }

  private static void createGroup(final CamundaClient camundaClient, final String group) {
    camundaClient.newCreateGroupCommand().groupId(group).name(group).send().join();
  }

  private static void createTenant(final CamundaClient camundaClient, final String tenant) {
    camundaClient.newCreateTenantCommand().tenantId(tenant).name(tenant).send().join();
  }

  private static void assignUserToTenant(
      final CamundaClient camundaClient, final String username, final String tenant) {
    camundaClient.newAssignUserToTenantCommand().username(username).tenantId(tenant).send().join();
  }

  private static void assignUserToGroup(
      final CamundaClient camundaClient, final String username, final String group) {
    camundaClient.newAssignUserToGroupCommand().username(username).groupId(group).send().join();
  }

  private static void waitForGroupsBeingExported(final CamundaClient camundaClient) {
    Awaitility.await("should receive data from secondary storage")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              assertThat(camundaClient.newGroupsSearchRequest().send().join().items()).hasSize(2);
            });
  }

  private static void waitForGroupMembershipsBeingExported(final CamundaClient camundaClient) {
    Awaitility.await("should receive data from secondary storage")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              assertThat(camundaClient.newUsersByGroupSearchRequest(GROUP_A).send().join().items())
                  .hasSize(1);
            });
  }
}
