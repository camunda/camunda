/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.qa.util.multidb.MultiDbTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

// regression test for https://github.com/camunda/camunda/issues/35549
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class UniqueEntityIdentifiersIT {

  private static CamundaClient client;

  @Test
  void shouldCreateUniqueIdsForTenantMembers() {
    // given
    final var conflictingId = "tenantMemberID";
    final String tenantId = "testTenant";
    // create group, role, and tenant
    client.newCreateGroupCommand().groupId(conflictingId).name("testGroup").send().join();
    client.newCreateRoleCommand().roleId(conflictingId).name("testRole").send().join();
    client.newCreateTenantCommand().tenantId(tenantId).name("testTenant").send().join();
    // assign group to tenant
    client.newAssignGroupToTenantCommand().groupId(conflictingId).tenantId(tenantId).send().join();
    Awaitility.await()
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var groupsResponse =
                  client.newGroupsByTenantSearchRequest(tenantId).send().join();
              assertThat(groupsResponse.items()).hasSize(1);
              assertThat(groupsResponse.items().get(0).getGroupId()).isEqualTo(conflictingId);
            });

    // when
    // assign role to tenant with the same ID
    client.newAssignRoleToTenantCommand().roleId(conflictingId).tenantId(tenantId).send().join();
    Awaitility.await()
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var rolesResponse =
                  client.newRolesByTenantSearchRequest(tenantId).send().join();
              assertThat(rolesResponse.items()).hasSize(1);
              assertThat(rolesResponse.items().get(0).getRoleId()).isEqualTo(conflictingId);
            });

    // then
    final var updatedGroupsResponse = client.newGroupsByTenantSearchRequest(tenantId).send().join();
    assertThat(updatedGroupsResponse.items()).hasSize(1);
    assertThat(updatedGroupsResponse.items().get(0).getGroupId()).isEqualTo(conflictingId);
  }

  @Test
  void shouldCreateUniqueIdsForGroupMembers() {
    // given
    final var conflictingId = "groupMemberID";
    final String groupId = "testGroup";
    // create user, client, and group
    client
        .newCreateUserCommand()
        .username(conflictingId)
        .name("testUser")
        .email("test@email.com")
        .password("fakePassword")
        .send()
        .join();
    client
        .newCreateMappingRuleCommand()
        .mappingRuleId(conflictingId)
        .name("testMapping")
        .claimName("testClaim")
        .claimValue("testValue")
        .send()
        .join();
    client.newCreateGroupCommand().groupId(groupId).name("Test Group").send().join();
    // assign user to group
    client.newAssignUserToGroupCommand().username(conflictingId).groupId(groupId).send().join();
    Awaitility.await()
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var usersResponse = client.newUsersByGroupSearchRequest(groupId).send().join();
              assertThat(usersResponse.items()).hasSize(1);
              assertThat(usersResponse.items().get(0).getUsername()).isEqualTo(conflictingId);
            });

    // when
    // assign client to group with the same ID
    client
        .newAssignMappingRuleToGroupCommand()
        .mappingRuleId(conflictingId)
        .groupId(groupId)
        .send()
        .join();
    Awaitility.await()
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var clientsResponse =
                  client.newMappingRulesByGroupSearchRequest(groupId).send().join();
              assertThat(clientsResponse.items()).hasSize(1);
              assertThat(clientsResponse.items().get(0).getMappingRuleId())
                  .isEqualTo(conflictingId);
            });

    // then
    final var updatedUsersResponse = client.newUsersByGroupSearchRequest(groupId).send().join();
    assertThat(updatedUsersResponse.items()).hasSize(1);
    assertThat(updatedUsersResponse.items().get(0).getUsername()).isEqualTo(conflictingId);
  }

  @Test
  void shouldCreateUniqueIdsForRoleMembers() {
    // given
    final var conflictingId = "roleMemberID";
    final String roleId = "testRole";
    // create user, group, and role
    client
        .newCreateUserCommand()
        .username(conflictingId)
        .name("testUser")
        .email("test@email.com")
        .password("fakePassword")
        .send()
        .join();
    client.newCreateGroupCommand().groupId(conflictingId).name("testGroup").send().join();
    client.newCreateRoleCommand().roleId(roleId).name("Test Role").send().join();
    // assign user to role
    client.newAssignRoleToUserCommand().roleId(roleId).username(conflictingId).send().join();
    Awaitility.await()
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var usersResponse = client.newUsersByRoleSearchRequest(roleId).send().join();
              assertThat(usersResponse.items()).hasSize(1);
              assertThat(usersResponse.items().get(0).getUsername()).isEqualTo(conflictingId);
            });

    // when
    // assign group to role with the same ID
    client.newAssignRoleToGroupCommand().roleId(roleId).groupId(conflictingId).send().join();
    Awaitility.await()
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var groupsResponse = client.newGroupsByRoleSearchRequest(roleId).send().join();
              assertThat(groupsResponse.items()).hasSize(1);
              assertThat(groupsResponse.items().get(0).getGroupId()).isEqualTo(conflictingId);
            });

    // then
    final var updatedUsersResponse = client.newUsersByRoleSearchRequest(roleId).send().join();
    assertThat(updatedUsersResponse.items()).hasSize(1);
    assertThat(updatedUsersResponse.items().get(0).getUsername()).isEqualTo(conflictingId);
  }
}
