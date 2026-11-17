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
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Role;
import io.camunda.client.api.search.response.RoleGroup;
import io.camunda.client.api.search.response.TenantGroup;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * A deleted group is removed from the roles and tenants it was a member of, and a deleted role from
 * its tenants, in the engine and in secondary storage. Memberships are keyed by ID, so a group or
 * role created again with the same ID must not be a member of anything.
 *
 * <p>The member searches read secondary storage, so they show that the exporters removed the
 * memberships. Assigning the re-created entity again succeeds only when the engine has no
 * membership left; otherwise it is rejected as already assigned.
 */
@MultiDbTest
public class DeletedGroupAndRoleMembershipsIT {

  private static CamundaClient camundaClient;

  @Test
  void shouldRemoveDeletedGroupFromItsRoleAndTenant() {
    // given a group that is a member of a role and of a tenant
    final var roleId = Strings.newRandomValidIdentityId();
    final var groupId = Strings.newRandomValidIdentityId();
    final var tenantId = Strings.newRandomValidTenantId();
    createRole(roleId);
    createGroup(groupId);
    createTenant(tenantId);
    assignGroupToRole(roleId, groupId);
    assignGroupToTenant(groupId, tenantId);
    Awaitility.await("the group is a member of the role and the tenant")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              assertThat(groupsOfRole(roleId)).contains(groupId);
              assertThat(groupsOfTenant(tenantId)).contains(groupId);
            });

    // when
    camundaClient.newDeleteGroupCommand(groupId).send().join();

    // then secondary storage no longer lists the group as a member
    Awaitility.await("the deleted group is no longer a member of the role and the tenant")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              assertThat(groupsOfRole(roleId)).doesNotContain(groupId);
              assertThat(groupsOfTenant(tenantId)).doesNotContain(groupId);
            });

    // and a group created again with the same ID inherited nothing in the engine
    createGroup(groupId);
    assertThatNoException()
        .as("the re-created group can be assigned to the role")
        .isThrownBy(() -> assignGroupToRole(roleId, groupId));
    assertThatNoException()
        .as("the re-created group can be assigned to the tenant")
        .isThrownBy(() -> assignGroupToTenant(groupId, tenantId));
  }

  @Test
  void shouldRemoveDeletedRoleFromItsTenant() {
    // given a role that is a member of a tenant
    final var roleId = Strings.newRandomValidIdentityId();
    final var tenantId = Strings.newRandomValidTenantId();
    createRole(roleId);
    createTenant(tenantId);
    assignRoleToTenant(roleId, tenantId);
    Awaitility.await("the role is a member of the tenant")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(() -> assertThat(rolesOfTenant(tenantId)).contains(roleId));

    // when
    camundaClient.newDeleteRoleCommand(roleId).send().join();

    // then secondary storage no longer lists the role as a member
    Awaitility.await("the deleted role is no longer a member of the tenant")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(() -> assertThat(rolesOfTenant(tenantId)).doesNotContain(roleId));

    // and a role created again with the same ID inherited nothing in the engine
    createRole(roleId);
    assertThatNoException()
        .as("the re-created role can be assigned to the tenant")
        .isThrownBy(() -> assignRoleToTenant(roleId, tenantId));
  }

  private static void createRole(final String roleId) {
    camundaClient.newCreateRoleCommand().roleId(roleId).name(roleId).send().join();
  }

  private static void createGroup(final String groupId) {
    camundaClient.newCreateGroupCommand().groupId(groupId).name(groupId).send().join();
  }

  private static void createTenant(final String tenantId) {
    camundaClient.newCreateTenantCommand().tenantId(tenantId).name(tenantId).send().join();
  }

  private static void assignGroupToRole(final String roleId, final String groupId) {
    camundaClient.newAssignRoleToGroupCommand().roleId(roleId).groupId(groupId).send().join();
  }

  private static void assignGroupToTenant(final String groupId, final String tenantId) {
    camundaClient.newAssignGroupToTenantCommand().groupId(groupId).tenantId(tenantId).send().join();
  }

  private static void assignRoleToTenant(final String roleId, final String tenantId) {
    camundaClient.newAssignRoleToTenantCommand().roleId(roleId).tenantId(tenantId).send().join();
  }

  private static List<String> groupsOfRole(final String roleId) {
    return camundaClient.newGroupsByRoleSearchRequest(roleId).send().join().items().stream()
        .map(RoleGroup::getGroupId)
        .toList();
  }

  private static List<String> groupsOfTenant(final String tenantId) {
    return camundaClient.newGroupsByTenantSearchRequest(tenantId).send().join().items().stream()
        .map(TenantGroup::getGroupId)
        .toList();
  }

  private static List<String> rolesOfTenant(final String tenantId) {
    return camundaClient.newRolesByTenantSearchRequest(tenantId).send().join().items().stream()
        .map(Role::getRoleId)
        .toList();
  }
}
