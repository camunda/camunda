/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class AuthorizationAddPermissionAuthorizationTest {

  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true))
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)));

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldBeAuthorizedToAddPermissionsWithDefaultUser() {
    // given
    final var user = createUser();

    // when
    engine
        .authorization()
        .permission()
        .withOwnerKey(user.getUserKey())
        .withResourceType(AuthorizationResourceType.RESOURCE)
        .withPermission(PermissionType.CREATE, "*")
        .add(DEFAULT_USER.getUsername());

    // then
    assertThat(
            RecordingExporter.authorizationRecords(AuthorizationIntent.PERMISSION_ADDED)
                .withOwnerKey(user.getUserKey())
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToAddPermissionsWithUser() {
    // given
    final var user = createUser();
    addPermissionToUser(
        user.getUserKey(), AuthorizationResourceType.AUTHORIZATION, PermissionType.UPDATE);

    // when
    engine
        .authorization()
        .permission()
        .withOwnerKey(user.getUserKey())
        .withResourceType(AuthorizationResourceType.RESOURCE)
        .withPermission(PermissionType.CREATE, "*")
        .add(user.getUsername());

    // then
    assertThat(
            RecordingExporter.authorizationRecords(AuthorizationIntent.PERMISSION_ADDED)
                .withOwnerKey(user.getUserKey())
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeForbiddenToAddPermissionsIfNoPermissions() {
    // given
    final var user = createUser();

    // when
    final var rejection =
        engine
            .authorization()
            .permission()
            .withOwnerKey(user.getUserKey())
            .withResourceType(AuthorizationResourceType.RESOURCE)
            .withPermission(PermissionType.DELETE, "*")
            .expectRejection()
            .add(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'UPDATE' on resource 'AUTHORIZATION'");
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

  private void addPermissionToUser(
      final long userKey,
      final AuthorizationResourceType authorization,
      final PermissionType permissionType) {
    engine
        .authorization()
        .permission()
        .withOwnerKey(userKey)
        .withResourceType(authorization)
        .withPermission(permissionType, "*")
        .add(DEFAULT_USER.getUsername());
  }
}
