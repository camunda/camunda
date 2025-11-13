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
import static io.camunda.migration.identity.config.saas.StaticEntities.TASK_USER_ROLE_ID;
import static io.camunda.migration.identity.config.saas.StaticEntities.VISITOR_ROLE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.Authorization;
import io.camunda.client.api.search.response.SearchResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SaaSIdentityMigrationWithRBAIT extends AbstractSaaSIdentityMigrationIT {

  @Override
  @BeforeEach
  public void setup(final WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    super.setup(wmRuntimeInfo);
    // given
    migration.withAppConfig(properties -> properties.setResourceAuthorizationsEnabled(true));
  }

  @Test
  public void canMigratePermissionsWithRBAEnabled() {
    // given

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
              assertThat(migratedAuthorizations.size()).isEqualTo(10);
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
                OPERATIONS_ENGINEER_ROLE_ID,
                OwnerType.ROLE,
                "operate",
                ResourceType.COMPONENT,
                Set.of(PermissionType.ACCESS)),
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
                ResourceType.DOCUMENT,
                Set.of(PermissionType.CREATE, PermissionType.READ)),
            tuple(
                TASK_USER_ROLE_ID,
                OwnerType.ROLE,
                "*",
                ResourceType.PROCESS_DEFINITION,
                Set.of(PermissionType.READ_USER_TASK, PermissionType.UPDATE_USER_TASK)),
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
                Set.of(PermissionType.READ)));
  }
}
