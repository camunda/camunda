/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static io.camunda.it.migration.IdentityMigrationTestUtil.IDENTITY_CLIENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.Authorization;
import io.camunda.client.api.search.response.Role;
import io.camunda.client.api.search.response.RoleGroup;
import io.camunda.client.api.search.response.RoleUser;
import io.camunda.client.api.search.response.Tenant;
import io.camunda.client.api.search.response.TenantUser;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class KeycloakIdentityMigrationIT extends AbstractKeycloakIdentityMigrationIT {

  @Test
  public void canMigrateRoles() {
    // when
    migration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var roles = client.newRolesSearchRequest().send().join();
              assertThat(roles.items())
                  .extracting(Role::getRoleId)
                  .contains("operate", "tasklist", "zeebe", "identity");

              final var authorizations = client.newAuthorizationSearchRequest().send().join();
              assertThat(authorizations.items())
                  .extracting(Authorization::getOwnerId)
                  .contains("operate", "tasklist", "zeebe", "identity");
            });

    // then
    assertThat(migration.getExitCode()).isEqualTo(0);

    final var roles = client.newRolesSearchRequest().send().join();
    assertThat(roles.items())
        .extracting(Role::getRoleId, Role::getName)
        .contains(
            tuple("operate", "Operate"),
            tuple("tasklist", "Tasklist"),
            tuple("zeebe", "Zeebe"),
            tuple("identity", "Identity"));

    final var authorizations = client.newAuthorizationSearchRequest().send().join();
    assertThat(authorizations.items())
        .extracting(
            Authorization::getOwnerId,
            Authorization::getResourceType,
            a -> new HashSet<>(a.getPermissionTypes()))
        .contains(
            tuple("operate", ResourceType.MESSAGE, Set.of(PermissionType.READ)),
            tuple(
                "operate",
                ResourceType.RESOURCE,
                Set.of(
                    PermissionType.READ,
                    PermissionType.DELETE_PROCESS,
                    PermissionType.DELETE_DRD,
                    PermissionType.DELETE_FORM,
                    PermissionType.DELETE_RESOURCE)),
            tuple(
                "operate",
                ResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.CREATE_DECISION_INSTANCE,
                    PermissionType.READ_DECISION_INSTANCE,
                    PermissionType.READ_DECISION_DEFINITION,
                    PermissionType.DELETE_DECISION_INSTANCE)),
            tuple("operate", ResourceType.COMPONENT, Set.of(PermissionType.ACCESS)),
            tuple(
                "operate",
                ResourceType.DECISION_REQUIREMENTS_DEFINITION,
                Set.of(PermissionType.READ)),
            tuple(
                "operate",
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.CREATE_PROCESS_INSTANCE,
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_PROCESS_INSTANCE,
                    PermissionType.UPDATE_PROCESS_INSTANCE,
                    PermissionType.MODIFY_PROCESS_INSTANCE,
                    PermissionType.CANCEL_PROCESS_INSTANCE,
                    PermissionType.DELETE_PROCESS_INSTANCE)),
            tuple(
                "operate",
                ResourceType.BATCH,
                Set.of(PermissionType.READ, PermissionType.CREATE, PermissionType.UPDATE)),
            tuple(
                "zeebe",
                ResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.CREATE_DECISION_INSTANCE,
                    PermissionType.DELETE_DECISION_INSTANCE)),
            tuple("zeebe", ResourceType.MESSAGE, Set.of(PermissionType.CREATE)),
            tuple(
                "zeebe",
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.CREATE_PROCESS_INSTANCE,
                    PermissionType.DELETE_PROCESS_INSTANCE,
                    PermissionType.UPDATE_PROCESS_INSTANCE,
                    PermissionType.UPDATE_USER_TASK)),
            tuple(
                "zeebe",
                ResourceType.RESOURCE,
                Set.of(
                    PermissionType.DELETE_FORM,
                    PermissionType.DELETE_PROCESS,
                    PermissionType.DELETE_DRD,
                    PermissionType.CREATE,
                    PermissionType.DELETE_RESOURCE)),
            tuple("zeebe", ResourceType.SYSTEM, Set.of(PermissionType.READ, PermissionType.UPDATE)),
            tuple(
                "tasklist",
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.READ_USER_TASK,
                    PermissionType.UPDATE_USER_TASK,
                    PermissionType.READ_PROCESS_DEFINITION)),
            tuple("tasklist", ResourceType.COMPONENT, Set.of(PermissionType.ACCESS)),
            tuple(
                "identity",
                ResourceType.GROUP,
                Set.of(
                    PermissionType.READ,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.CREATE)),
            tuple(
                "identity",
                ResourceType.TENANT,
                Set.of(
                    PermissionType.READ,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.CREATE)),
            tuple(
                "identity",
                ResourceType.ROLE,
                Set.of(
                    PermissionType.READ,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.CREATE)),
            tuple(
                "identity",
                ResourceType.AUTHORIZATION,
                Set.of(
                    PermissionType.READ,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.CREATE)),
            tuple(
                "identity",
                ResourceType.MAPPING_RULE,
                Set.of(
                    PermissionType.READ,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.CREATE)),
            tuple("identity", ResourceType.USER, Set.of(PermissionType.READ)),
            tuple("identity", ResourceType.COMPONENT, Set.of(PermissionType.ACCESS)));
  }

  @Test
  public void canMigrateRolesWithRBAEnabled() {
    // given
    migration.withAppConfig(properties -> properties.setResourceAuthorizationsEnabled(true));

    // when
    migration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var roles = client.newRolesSearchRequest().send().join();
              assertThat(roles.items())
                  .extracting(Role::getRoleId)
                  .contains("operate", "tasklist", "zeebe", "identity");

              final var authorizations = client.newAuthorizationSearchRequest().send().join();
              assertThat(authorizations.items())
                  .extracting(Authorization::getOwnerId)
                  .contains("operate", "tasklist", "zeebe", "identity");
            });

    // then
    assertThat(migration.getExitCode()).isEqualTo(0);

    final var roles = client.newRolesSearchRequest().send().join();
    assertThat(roles.items())
        .extracting(Role::getRoleId, Role::getName)
        .contains(
            tuple("operate", "Operate"),
            tuple("tasklist", "Tasklist"),
            tuple("zeebe", "Zeebe"),
            tuple("identity", "Identity"));

    final var authorizations = client.newAuthorizationSearchRequest().send().join();
    assertThat(authorizations.items())
        .extracting(
            Authorization::getOwnerId,
            Authorization::getResourceType,
            a -> new HashSet<>(a.getPermissionTypes()))
        .contains(
            tuple("operate", ResourceType.COMPONENT, Set.of(PermissionType.ACCESS)),
            tuple("zeebe", ResourceType.SYSTEM, Set.of(PermissionType.READ, PermissionType.UPDATE)),
            tuple("tasklist", ResourceType.COMPONENT, Set.of(PermissionType.ACCESS)),
            tuple(
                "identity",
                ResourceType.GROUP,
                Set.of(
                    PermissionType.READ,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.CREATE)),
            tuple(
                "identity",
                ResourceType.TENANT,
                Set.of(
                    PermissionType.READ,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.CREATE)),
            tuple(
                "identity",
                ResourceType.ROLE,
                Set.of(
                    PermissionType.READ,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.CREATE)),
            tuple(
                "identity",
                ResourceType.AUTHORIZATION,
                Set.of(
                    PermissionType.READ,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.CREATE)),
            tuple(
                "identity",
                ResourceType.MAPPING_RULE,
                Set.of(
                    PermissionType.READ,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.CREATE)),
            tuple("identity", ResourceType.USER, Set.of(PermissionType.READ)),
            tuple("identity", ResourceType.COMPONENT, Set.of(PermissionType.ACCESS)));
  }

  @Test
  public void canMigrateGroups() {
    // when
    migration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var authorizations = client.newAuthorizationSearchRequest().send().join();
              assertThat(authorizations.items())
                  .extracting(Authorization::getOwnerId)
                  .contains("groupa", "groupb");
            });

    // then
    assertThat(migration.getExitCode()).isEqualTo(0);

    final var authorizations = client.newAuthorizationSearchRequest().send().join();
    assertThat(authorizations.items())
        .extracting(
            Authorization::getOwnerId,
            Authorization::getOwnerType,
            Authorization::getResourceType,
            a -> new HashSet<>(a.getPermissionTypes()))
        .contains(
            tuple(
                "groupa",
                OwnerType.GROUP,
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_PROCESS_INSTANCE,
                    PermissionType.DELETE_PROCESS_INSTANCE)),
            tuple(
                "groupb",
                OwnerType.GROUP,
                ResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.DELETE_DECISION_INSTANCE,
                    PermissionType.READ_DECISION_INSTANCE,
                    PermissionType.READ_DECISION_DEFINITION)));
  }

  @Test
  public void canMigrateGroupsRolesMembership()
      throws URISyntaxException, IOException, InterruptedException {
    // given
    // we need to add the group-role memberships here via API calls, because there is no support to
    // do that via configuration
    addGroupToRoleInManagementIdentity("groupc", "Zeebe");
    addGroupToRoleInManagementIdentity("groupb", "Tasklist");
    addGroupToRoleInManagementIdentity("groupb", "Operate");
    addGroupToRoleInManagementIdentity("groupa", "Identity");

    // when
    migration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var groups = client.newGroupsByRoleSearchRequest("zeebe").send().join();
              assertThat(groups.items()).extracting(RoleGroup::getGroupId).contains("groupc");
            });

    // then
    assertThat(migration.getExitCode()).isEqualTo(0);

    final var zeebeGroups = client.newGroupsByRoleSearchRequest("zeebe").send().join().items();
    assertThat(zeebeGroups).extracting(RoleGroup::getGroupId).containsExactlyInAnyOrder("groupc");
    final var operateGroups = client.newGroupsByRoleSearchRequest("operate").send().join().items();
    assertThat(operateGroups).extracting(RoleGroup::getGroupId).containsExactlyInAnyOrder("groupb");
    final var tasklistGroups =
        client.newGroupsByRoleSearchRequest("tasklist").send().join().items();
    assertThat(tasklistGroups)
        .extracting(RoleGroup::getGroupId)
        .containsExactlyInAnyOrder("groupb");
    final var identityGroups =
        client.newGroupsByRoleSearchRequest("identity").send().join().items();
    assertThat(identityGroups)
        .extracting(RoleGroup::getGroupId)
        .containsExactlyInAnyOrder("groupa");
  }

  @Test
  public void canMigrateUsersRolesMembership() {
    // when
    migration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var users = client.newUsersByRoleSearchRequest("zeebe").send().join();
              assertThat(users.items()).hasSize(2);
            });

    // then
    assertThat(migration.getExitCode()).isEqualTo(0);
    final var zeebeUsers = client.newUsersByRoleSearchRequest("zeebe").send().join().items();
    assertThat(zeebeUsers)
        .extracting(RoleUser::getUsername)
        .containsExactlyInAnyOrder("user0", "user1");
    final var operateUsers = client.newUsersByRoleSearchRequest("operate").send().join().items();
    assertThat(operateUsers).extracting(RoleUser::getUsername).containsExactlyInAnyOrder("user0");
    final var tasklistUsers = client.newUsersByRoleSearchRequest("tasklist").send().join().items();
    assertThat(tasklistUsers).extracting(RoleUser::getUsername).containsExactlyInAnyOrder("user0");
    final var identityUsers = client.newUsersByRoleSearchRequest("identity").send().join().items();
    assertThat(identityUsers).extracting(RoleUser::getUsername).containsExactlyInAnyOrder("user0");
  }

  @Test
  public void canMigrateAuthorizations() {
    // when
    migration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var authorizations = client.newAuthorizationSearchRequest().send().join();
              assertThat(authorizations.items())
                  .extracting(Authorization::getOwnerId)
                  .contains("user0", "user1");
            });

    final var authorizations = client.newAuthorizationSearchRequest().send().join();
    assertThat(authorizations.items())
        .extracting(
            Authorization::getOwnerId,
            Authorization::getOwnerType,
            Authorization::getResourceId,
            Authorization::getResourceType,
            a -> new HashSet<>(a.getPermissionTypes()))
        .contains(
            tuple(
                "user0",
                OwnerType.USER,
                "*",
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_PROCESS_INSTANCE,
                    PermissionType.UPDATE_PROCESS_INSTANCE)),
            tuple(
                "user1",
                OwnerType.USER,
                "*",
                ResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.DELETE_DECISION_INSTANCE,
                    PermissionType.READ_DECISION_INSTANCE,
                    PermissionType.READ_DECISION_DEFINITION)));
  }

  @Test
  public void canMigrateClients() {
    // when
    migration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var authorizations = client.newAuthorizationSearchRequest().send().join();
              assertThat(authorizations.items())
                  .extracting(Authorization::getOwnerId)
                  .contains("migration-app");
            });

    // then
    assertThat(migration.getExitCode()).isEqualTo(0);

    final var authorizations = client.newAuthorizationSearchRequest().send().join();
    assertThat(authorizations.items())
        .extracting(
            Authorization::getOwnerId,
            Authorization::getOwnerType,
            Authorization::getResourceType,
            a -> new HashSet<>(a.getPermissionTypes()))
        .contains(
            tuple(
                "migration-app",
                OwnerType.CLIENT,
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.UPDATE_PROCESS_INSTANCE,
                    PermissionType.UPDATE_USER_TASK,
                    PermissionType.DELETE_PROCESS_INSTANCE,
                    PermissionType.CREATE_PROCESS_INSTANCE)),
            tuple(
                "migration-app",
                OwnerType.CLIENT,
                ResourceType.RESOURCE,
                Set.of(
                    PermissionType.CREATE,
                    PermissionType.DELETE_FORM,
                    PermissionType.DELETE_RESOURCE,
                    PermissionType.DELETE_DRD,
                    PermissionType.DELETE_PROCESS)),
            tuple(
                "migration-app", OwnerType.CLIENT, ResourceType.USER, Set.of(PermissionType.READ)),
            tuple(
                "migration-app",
                OwnerType.CLIENT,
                ResourceType.COMPONENT,
                Set.of(PermissionType.ACCESS)),
            tuple(
                "migration-app",
                OwnerType.CLIENT,
                ResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.CREATE_DECISION_INSTANCE,
                    PermissionType.DELETE_DECISION_INSTANCE)),
            tuple(
                "migration-app",
                OwnerType.CLIENT,
                ResourceType.AUTHORIZATION,
                Set.of(
                    PermissionType.CREATE,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.READ)),
            tuple(
                "migration-app",
                OwnerType.CLIENT,
                ResourceType.GROUP,
                Set.of(
                    PermissionType.CREATE,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.READ)),
            tuple(
                "migration-app",
                OwnerType.CLIENT,
                ResourceType.TENANT,
                Set.of(
                    PermissionType.CREATE,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.READ)),
            tuple(
                "migration-app",
                OwnerType.CLIENT,
                ResourceType.SYSTEM,
                Set.of(PermissionType.UPDATE, PermissionType.READ)),
            tuple(
                "migration-app",
                OwnerType.CLIENT,
                ResourceType.ROLE,
                Set.of(
                    PermissionType.CREATE,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.READ)),
            tuple(
                "migration-app",
                OwnerType.CLIENT,
                ResourceType.MESSAGE,
                Set.of(PermissionType.CREATE)));
  }

  @Test
  public void canMigrateTenants() throws URISyntaxException, IOException, InterruptedException {
    // when
    migration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var tenants = client.newTenantsSearchRequest().send().join();
              assertThat(tenants.items())
                  .extracting(Tenant::getTenantId)
                  .contains("tenant1", "TenanT2");
            });

    // then
    assertThat(migration.getExitCode()).isEqualTo(0);

    final var tenants = client.newTenantsSearchRequest().send().join().items();
    assertThat(tenants)
        .extracting(Tenant::getTenantId, Tenant::getName)
        .contains(tuple("tenant1", "tenant 1"), tuple("TenanT2", "tenant 2"));

    final var tenant1Users = client.newUsersByTenantSearchRequest("tenant1").send().join();
    assertThat(tenant1Users.items())
        .extracting(TenantUser::getUsername)
        .containsExactlyInAnyOrder("user0");
    final var tenant2Users = client.newUsersByTenantSearchRequest("TenanT2").send().join();
    assertThat(tenant2Users.items())
        .extracting(TenantUser::getUsername)
        .containsExactlyInAnyOrder("user1");

    final var restAddress = client.getConfiguration().getRestAddress().toString();
    final var tenant1Groups = searchGroupsInTenant(restAddress, "tenant1");
    assertThat(tenant1Groups.items())
        .extracting(TenantGroup::groupId)
        .containsExactlyInAnyOrder("groupa");
    final var tenant2Groups = searchGroupsInTenant(restAddress, "TenanT2");
    assertThat(tenant2Groups.items())
        .extracting(TenantGroup::groupId)
        .containsExactlyInAnyOrder("groupb");
    final var tenant1Clients = searchClientsInTenant(restAddress, "tenant1");
    assertThat(tenant1Clients.items())
        .extracting(TenantClient::clientId)
        .containsExactlyInAnyOrder(IDENTITY_CLIENT);
    final var tenant2Clients = searchClientsInTenant(restAddress, "TenanT2");
    assertThat(tenant2Clients.items())
        .extracting(TenantClient::clientId)
        .containsExactlyInAnyOrder(IDENTITY_CLIENT);
  }
}
