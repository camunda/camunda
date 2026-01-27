/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clustervariable;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.EngineRule.ResetRecordingExporterMode;
import io.camunda.zeebe.engine.util.EngineRule.ResetRecordingExporterTestWatcherMode;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;

public class UpdateClusterVariableAuthorizationTest {
  private static final String VARIABLE_NAME = "myVariable";
  private static final String VARIABLE_VALUE = "\"myValue\"";
  private static final String UPDATED_VALUE = "\"updatedValue\"";
  private static final String TENANT_ID = "tenant_1";

  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withResetRecordingExporterTestWatcherMode(
              ResetRecordingExporterTestWatcherMode.BEFORE_ALL_TESTS_AND_AFTER_EACH_TEST)
          .withIdentitySetup(ResetRecordingExporterMode.NO_RESET_AFTER_IDENTITY_SETUP)
          .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true))
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)))
          .withSecurityConfig(
              cfg ->
                  cfg.getInitialization()
                      .getDefaultRoles()
                      .put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername()))));

  @Test
  public void shouldBeAuthorizedToUpdateGlobalScopedClusterVariable() {
    // given
    engine
        .clusterVariables()
        .withName(VARIABLE_NAME)
        .setGlobalScope()
        .withValue(VARIABLE_VALUE)
        .create(DEFAULT_USER.getUsername());

    final var user = createUser();
    addPermissionsToUser(
        user,
        AuthorizationResourceType.CLUSTER_VARIABLE,
        PermissionType.UPDATE,
        AuthorizationResourceMatcher.ANY,
        "*");

    // when
    engine
        .clusterVariables()
        .withName(VARIABLE_NAME)
        .setGlobalScope()
        .withValue(UPDATED_VALUE)
        .update(user.getUsername());

    // then
    assertThat(
            RecordingExporter.clusterVariableRecords()
                .withIntent(ClusterVariableIntent.UPDATED)
                .withName(VARIABLE_NAME)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToUpdateTenantScopedClusterVariable() {
    // given
    engine
        .clusterVariables()
        .withName(VARIABLE_NAME)
        .setTenantScope()
        .withTenantId(TENANT_ID)
        .withValue(VARIABLE_VALUE)
        .create(DEFAULT_USER.getUsername());

    final var user = createUser();
    addPermissionsToUser(
        user,
        AuthorizationResourceType.CLUSTER_VARIABLE,
        PermissionType.UPDATE,
        AuthorizationResourceMatcher.ANY,
        "*");

    // when
    engine
        .clusterVariables()
        .withName(VARIABLE_NAME)
        .setTenantScope()
        .withTenantId(TENANT_ID)
        .withValue(UPDATED_VALUE)
        .update(user.getUsername());

    // then
    assertThat(
            RecordingExporter.clusterVariableRecords()
                .withIntent(ClusterVariableIntent.UPDATED)
                .withName(VARIABLE_NAME)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToUpdateGlobalScopedClusterVariableWithSpecificPermissions() {
    // given
    engine
        .clusterVariables()
        .withName(VARIABLE_NAME + "_specific")
        .setGlobalScope()
        .withValue(VARIABLE_VALUE)
        .create(DEFAULT_USER.getUsername());

    final var user = createUser();
    addPermissionsToUser(
        user,
        AuthorizationResourceType.CLUSTER_VARIABLE,
        PermissionType.UPDATE,
        AuthorizationResourceMatcher.ID,
        VARIABLE_NAME + "_specific");

    // when
    engine
        .clusterVariables()
        .withName(VARIABLE_NAME + "_specific")
        .setGlobalScope()
        .withValue(UPDATED_VALUE)
        .update(user.getUsername());

    // then
    assertThat(
            RecordingExporter.clusterVariableRecords()
                .withIntent(ClusterVariableIntent.UPDATED)
                .withName(VARIABLE_NAME + "_specific")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToUpdateTenantScopedClusterVariableWithSpecificPermissions() {
    // given
    engine
        .clusterVariables()
        .withName(VARIABLE_NAME + "_specific")
        .setTenantScope()
        .withTenantId(TENANT_ID)
        .withValue(VARIABLE_VALUE)
        .create(DEFAULT_USER.getUsername());

    final var user = createUser();
    addPermissionsToUser(
        user,
        AuthorizationResourceType.CLUSTER_VARIABLE,
        PermissionType.UPDATE,
        AuthorizationResourceMatcher.ID,
        VARIABLE_NAME + "_specific");

    // when
    engine
        .clusterVariables()
        .withName(VARIABLE_NAME + "_specific")
        .setTenantScope()
        .withTenantId(TENANT_ID)
        .withValue(UPDATED_VALUE)
        .update(user.getUsername());

    // then
    assertThat(
            RecordingExporter.clusterVariableRecords()
                .withIntent(ClusterVariableIntent.UPDATED)
                .withName(VARIABLE_NAME + "_specific")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToUpdateGlobalScopedClusterVariableIfNoPermissions() {
    // given
    engine
        .clusterVariables()
        .withName(VARIABLE_NAME)
        .setGlobalScope()
        .withValue(VARIABLE_VALUE)
        .create(DEFAULT_USER.getUsername());

    final var user = createUser();

    // when
    final var rejection =
        engine
            .clusterVariables()
            .withName(VARIABLE_NAME)
            .setGlobalScope()
            .withValue(UPDATED_VALUE)
            .expectRejection()
            .update(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'UPDATE' on resource 'CLUSTER_VARIABLE', required resource identifiers are one of '[*, myVariable]'");
  }

  @Test
  public void shouldBeUnauthorizedToUpdateTenantScopedClusterVariableIfNoPermissions() {
    // given
    engine
        .clusterVariables()
        .withName(VARIABLE_NAME)
        .setTenantScope()
        .withTenantId(TENANT_ID)
        .withValue(VARIABLE_VALUE)
        .create(DEFAULT_USER.getUsername());

    final var user = createUser();

    // when
    final var rejection =
        engine
            .clusterVariables()
            .withName(VARIABLE_NAME)
            .setTenantScope()
            .withTenantId(TENANT_ID)
            .withValue(UPDATED_VALUE)
            .expectRejection()
            .update(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'UPDATE' on resource 'CLUSTER_VARIABLE', required resource identifiers are one of '[*, myVariable]'");
  }

  private UserRecordValue createUser() {
    return engine
        .user()
        .newUser(UUID.randomUUID().toString())
        .withPassword(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .withEmail(UUID.randomUUID().toString())
        .create()
        .getValue();
  }

  private void addPermissionsToUser(
      final UserRecordValue user,
      final AuthorizationResourceType authorizationType,
      final PermissionType permissionType,
      final AuthorizationResourceMatcher matcher,
      final String resourceId) {
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(permissionType)
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(authorizationType)
        .withResourceMatcher(matcher)
        .withResourceId(resourceId)
        .create();
  }
}
