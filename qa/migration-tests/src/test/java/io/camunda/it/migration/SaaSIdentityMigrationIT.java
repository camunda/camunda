/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static io.camunda.migration.identity.config.saas.StaticEntities.DEVELOPER_ROLE_ID;
import static io.camunda.migration.identity.config.saas.StaticEntities.OPERATIONS_ENGINEER_ROLE_ID;
import static io.camunda.migration.identity.config.saas.StaticEntities.ROLE_IDS;
import static io.camunda.migration.identity.config.saas.StaticEntities.ROLE_PERMISSIONS;
import static io.camunda.migration.identity.config.saas.StaticEntities.TASK_USER_ROLE_ID;
import static io.camunda.migration.identity.config.saas.StaticEntities.VISITOR_ROLE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.Authorization;
import io.camunda.client.api.search.response.Group;
import io.camunda.client.api.search.response.RoleUser;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.migration.identity.client.ConsoleClient.Role;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@WireMockTest
@ZeebeIntegration
@Testcontainers(parallel = true)
public class SaaSIdentityMigrationIT extends AbstractSaaSIdentityMigrationIT {

  @Test
  void canMigrateGroups() throws IOException, URISyntaxException, InterruptedException {
    // when
    createGroups();
    assignGroupsToUsers();

    migration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var groups = client.newGroupsSearchRequest().send().join();
              assertThat(groups.items().size()).isEqualTo(3);
            });

    // then
    assertThat(migration.getExitCode()).isEqualTo(0);

    final var groups = client.newGroupsSearchRequest().send().join();
    assertThat(groups.items().size()).isEqualTo(3);
    assertThat(groups.items())
        .map(Group::getGroupId, Group::getName)
        .containsExactly(
            tuple("groupa", "groupA"), tuple("groupb", "groupB"), tuple("groupc", "groupC"));
    final var userA = client.newUsersByGroupSearchRequest("groupa").send().join();
    assertThat(userA.items().size()).isEqualTo(1);
    assertThat(userA.items().getFirst().getUsername()).isEqualTo("user0@email.com");
    final var userB = client.newUsersByGroupSearchRequest("groupb").send().join();
    assertThat(userB.items().size()).isEqualTo(1);
    assertThat(userB.items().getFirst().getUsername()).isEqualTo("user0@email.com");
    final var userC = client.newUsersByGroupSearchRequest("groupc").send().join();
    assertThat(userC.items().size()).isEqualTo(1);
    assertThat(userC.items().getFirst().getUsername()).isEqualTo("user1@email.com");
  }

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
                  .map(io.camunda.client.api.search.response.Role::getRoleId)
                  .contains(
                      Role.DEVELOPER.getName(),
                      Role.OPERATIONS_ENGINEER.getName(),
                      Role.TASK_USER.getName(),
                      Role.VISITOR.getName());
            });

    // then
    assertThat(migration.getExitCode()).isEqualTo(0);

    final var roles = client.newRolesSearchRequest().send().join();
    assertThat(roles.items())
        .map(
            io.camunda.client.api.search.response.Role::getRoleId,
            io.camunda.client.api.search.response.Role::getName)
        .contains(
            tuple(Role.DEVELOPER.getName(), "Developer"),
            tuple(Role.OPERATIONS_ENGINEER.getName(), "Operations Engineer"),
            tuple(Role.TASK_USER.getName(), "Task User"),
            tuple(Role.VISITOR.getName(), "Visitor"));
    final var members = client.newUsersByRoleSearchRequest("admin").send().join();
    assertThat(members.items())
        .map(RoleUser::getUsername)
        .contains("user0@email.com", "user1@email.com");
    final var members2 = client.newUsersByRoleSearchRequest("developer").send().join();
    assertThat(members2.items().size()).isEqualTo(2);
    assertThat(members2.items())
        .map(RoleUser::getUsername)
        .contains("user0@email.com", "user1@email.com");
  }

  @Test
  public void canMigratePermissions() {
    // when
    migration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var authorizations =
                  client.newAuthorizationSearchRequest().send().join().items();
              final var migratedAuthorizations =
                  authorizations.stream()
                      .map(Authorization::getOwnerId)
                      .filter(ROLE_IDS::contains)
                      .toList();
              assertThat(migratedAuthorizations.size()).isEqualTo(ROLE_PERMISSIONS.size());
            });

    // then
    assertThat(migration.getExitCode()).isEqualTo(0);

    final SearchResponse<Authorization> newResponse =
        client.newAuthorizationSearchRequest().send().join();
    assertThat(newResponse.items())
        .extracting(
            Authorization::getOwnerId,
            Authorization::getOwnerType,
            Authorization::getResourceId,
            Authorization::getResourceType,
            a -> new HashSet<>(a.getPermissionTypes()))
        .contains(
            // Role permissions
            tuple(
                DEVELOPER_ROLE_ID,
                OwnerType.ROLE,
                "operate",
                ResourceType.COMPONENT,
                Set.of(PermissionType.ACCESS)),
            tuple(
                DEVELOPER_ROLE_ID,
                OwnerType.ROLE,
                "tasklist",
                ResourceType.COMPONENT,
                Set.of(PermissionType.ACCESS)),
            tuple(
                DEVELOPER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.DOCUMENT,
                Set.of(PermissionType.CREATE, PermissionType.READ)),
            tuple(
                DEVELOPER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_PROCESS_INSTANCE,
                    PermissionType.READ_USER_TASK,
                    PermissionType.UPDATE_PROCESS_INSTANCE,
                    PermissionType.UPDATE_USER_TASK,
                    PermissionType.CREATE_PROCESS_INSTANCE,
                    PermissionType.CANCEL_PROCESS_INSTANCE,
                    PermissionType.MODIFY_PROCESS_INSTANCE,
                    PermissionType.DELETE_PROCESS_INSTANCE)),
            tuple(
                DEVELOPER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.READ_DECISION_DEFINITION,
                    PermissionType.READ_DECISION_INSTANCE,
                    PermissionType.CREATE_DECISION_INSTANCE,
                    PermissionType.DELETE_DECISION_INSTANCE)),
            tuple(
                DEVELOPER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.DECISION_REQUIREMENTS_DEFINITION,
                Set.of(PermissionType.READ)),
            tuple(
                DEVELOPER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.RESOURCE,
                Set.of(
                    PermissionType.CREATE,
                    PermissionType.DELETE_FORM,
                    PermissionType.DELETE_PROCESS,
                    PermissionType.DELETE_DRD,
                    PermissionType.DELETE_RESOURCE,
                    PermissionType.READ)),
            tuple(
                DEVELOPER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.BATCH,
                Set.of(PermissionType.CREATE, PermissionType.READ, PermissionType.UPDATE)),
            tuple(
                DEVELOPER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.MESSAGE,
                Set.of(PermissionType.READ)),
            tuple(
                OPERATIONS_ENGINEER_ROLE_ID,
                OwnerType.ROLE,
                "operate",
                ResourceType.COMPONENT,
                Set.of(PermissionType.ACCESS)),
            tuple(
                OPERATIONS_ENGINEER_ROLE_ID,
                OwnerType.ROLE,
                "*",
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
                OPERATIONS_ENGINEER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.READ_DECISION_DEFINITION,
                    PermissionType.READ_DECISION_INSTANCE,
                    PermissionType.CREATE_DECISION_INSTANCE,
                    PermissionType.DELETE_DECISION_INSTANCE)),
            tuple(
                OPERATIONS_ENGINEER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.DECISION_REQUIREMENTS_DEFINITION,
                Set.of(PermissionType.READ)),
            tuple(
                OPERATIONS_ENGINEER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.RESOURCE,
                Set.of(
                    PermissionType.READ,
                    PermissionType.DELETE_RESOURCE,
                    PermissionType.DELETE_PROCESS,
                    PermissionType.DELETE_DRD,
                    PermissionType.DELETE_FORM)),
            tuple(
                OPERATIONS_ENGINEER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.BATCH,
                Set.of(PermissionType.CREATE, PermissionType.READ, PermissionType.UPDATE)),
            tuple(
                OPERATIONS_ENGINEER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.MESSAGE,
                Set.of(PermissionType.READ)),
            tuple(
                TASK_USER_ROLE_ID,
                OwnerType.ROLE,
                "tasklist",
                ResourceType.COMPONENT,
                Set.of(PermissionType.ACCESS)),
            tuple(
                TASK_USER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_USER_TASK,
                    PermissionType.UPDATE_USER_TASK,
                    PermissionType.CREATE_PROCESS_INSTANCE)),
            tuple(
                VISITOR_ROLE_ID,
                OwnerType.ROLE,
                "operate",
                ResourceType.COMPONENT,
                Set.of(PermissionType.ACCESS)),
            tuple(
                VISITOR_ROLE_ID,
                OwnerType.ROLE,
                "tasklist",
                ResourceType.COMPONENT,
                Set.of(PermissionType.ACCESS)),
            tuple(
                VISITOR_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.DOCUMENT,
                Set.of(PermissionType.READ)),
            tuple(
                VISITOR_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_PROCESS_INSTANCE,
                    PermissionType.READ_USER_TASK)),
            tuple(
                VISITOR_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.READ_DECISION_DEFINITION,
                    PermissionType.READ_DECISION_INSTANCE)),
            tuple(
                VISITOR_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.DECISION_REQUIREMENTS_DEFINITION,
                Set.of(PermissionType.READ)),
            tuple(
                VISITOR_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.BATCH,
                Set.of(PermissionType.READ)),
            tuple(
                VISITOR_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.MESSAGE,
                Set.of(PermissionType.READ)),
            tuple(
                VISITOR_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.RESOURCE,
                Set.of(PermissionType.READ)));
  }

  @Test
  public void canMigrateAuthorizations()
      throws URISyntaxException, IOException, InterruptedException {
    // when
    createAuthorizations();

    migration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final SearchResponse<Authorization> authorizations =
                  client.newAuthorizationSearchRequest().send().join();
              final var migratedAuthorizations =
                  authorizations.items().stream()
                      .map(Authorization::getOwnerId)
                      .filter(id -> List.of("user0@email.com", "user1@email.com").contains(id))
                      .toList();
              assertThat(migratedAuthorizations.size()).isEqualTo(2);
            });

    // then
    assertThat(migration.getExitCode()).isEqualTo(0);

    final SearchResponse<Authorization> response =
        client.newAuthorizationSearchRequest().send().join();
    assertThat(response.items())
        .map(
            Authorization::getOwnerId,
            Authorization::getOwnerType,
            Authorization::getResourceId,
            Authorization::getResourceType,
            a -> new HashSet<>(a.getPermissionTypes()))
        .contains(
            tuple(
                "user0@email.com",
                OwnerType.USER,
                "my-test-resource",
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_PROCESS_INSTANCE,
                    PermissionType.UPDATE_PROCESS_INSTANCE,
                    PermissionType.CREATE_PROCESS_INSTANCE,
                    PermissionType.DELETE_PROCESS_INSTANCE)),
            tuple(
                "user1@email.com",
                OwnerType.USER,
                "another-test-resource",
                ResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.READ_DECISION_DEFINITION,
                    PermissionType.READ_DECISION_INSTANCE,
                    PermissionType.DELETE_DECISION_INSTANCE)));
  }

  @Test
  public void canMigrateClients() {
    // when
    migration.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var authorizations =
                  client.newAuthorizationSearchRequest().send().join().items().stream()
                      .map(Authorization::getOwnerType)
                      .filter(OwnerType.CLIENT::equals)
                      .toList();
              assertThat(authorizations.size()).isEqualTo(10);
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
                "client123",
                OwnerType.CLIENT,
                ResourceType.MESSAGE,
                Set.of(PermissionType.CREATE, PermissionType.READ)),
            tuple(
                "client123",
                OwnerType.CLIENT,
                ResourceType.SYSTEM,
                Set.of(
                    PermissionType.UPDATE, PermissionType.READ, PermissionType.READ_USAGE_METRIC)),
            tuple(
                "client123",
                OwnerType.CLIENT,
                ResourceType.RESOURCE,
                Set.of(
                    PermissionType.CREATE,
                    PermissionType.DELETE_FORM,
                    PermissionType.DELETE_PROCESS,
                    PermissionType.DELETE_DRD,
                    PermissionType.DELETE_RESOURCE,
                    PermissionType.READ)),
            tuple(
                "client123",
                OwnerType.CLIENT,
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.UPDATE_PROCESS_INSTANCE,
                    PermissionType.UPDATE_USER_TASK,
                    PermissionType.CREATE_PROCESS_INSTANCE,
                    PermissionType.DELETE_PROCESS_INSTANCE,
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_PROCESS_INSTANCE)),
            tuple(
                "client123",
                OwnerType.CLIENT,
                ResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.CREATE_DECISION_INSTANCE,
                    PermissionType.DELETE_DECISION_INSTANCE,
                    PermissionType.READ_DECISION_DEFINITION,
                    PermissionType.READ_DECISION_INSTANCE)),
            tuple(
                "client123",
                OwnerType.CLIENT,
                ResourceType.DECISION_REQUIREMENTS_DEFINITION,
                Set.of(PermissionType.READ)),
            tuple(
                "client123",
                OwnerType.CLIENT,
                ResourceType.BATCH,
                Set.of(PermissionType.READ, PermissionType.CREATE, PermissionType.UPDATE)),
            tuple(
                "tasklist-client",
                OwnerType.CLIENT,
                ResourceType.DOCUMENT,
                Set.of(PermissionType.CREATE, PermissionType.READ, PermissionType.DELETE)),
            tuple(
                "tasklist-client",
                OwnerType.CLIENT,
                ResourceType.RESOURCE,
                Set.of(PermissionType.READ)),
            tuple(
                "tasklist-client",
                OwnerType.CLIENT,
                ResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_USER_TASK,
                    PermissionType.UPDATE_USER_TASK)));
  }
}
