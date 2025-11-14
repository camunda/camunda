/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.Authorization;
import io.camunda.client.api.search.response.Role;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class KeycloakIdentityMigrationWithRBAIT extends AbstractKeycloakIdentityMigrationIT {

  @Override
  @BeforeEach
  public void setup() throws Exception {
    super.setup();
    // given
    migration.withAppConfig(properties -> properties.setResourceAuthorizationsEnabled(true));
  }

  @Test
  public void canMigrateRolesWithRBAEnabled() {
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
                "tasklist",
                ResourceType.DOCUMENT,
                Set.of(PermissionType.CREATE, PermissionType.READ, PermissionType.DELETE)),
            tuple(
                "tasklist",
                ResourceType.PROCESS_DEFINITION,
                Set.of(PermissionType.READ_USER_TASK, PermissionType.UPDATE_USER_TASK)),
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
}
