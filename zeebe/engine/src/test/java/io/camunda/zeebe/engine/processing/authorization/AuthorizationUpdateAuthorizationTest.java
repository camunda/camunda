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
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
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

public class AuthorizationUpdateAuthorizationTest {

  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withIdentitySetup()
          .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true))
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)));

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldBeAuthorizedToUpdateAuthorizationWithDefaultUser() {
    // given
    final var user = createUser();
    final var authorizationKey =
        engine
            .authorization()
            .newAuthorization()
            .withOwnerId(user.getUsername())
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceType(AuthorizationResourceType.RESOURCE)
            .withResourceId("*")
            .withPermissions(PermissionType.CREATE)
            .create(DEFAULT_USER.getUsername())
            .getValue()
            .getAuthorizationKey();

    // when
    engine
        .authorization()
        .updateAuthorization(authorizationKey)
        .withPermissions(PermissionType.DELETE_FORM)
        .update(DEFAULT_USER.getUsername());

    // then
    assertThat(
            RecordingExporter.authorizationRecords(AuthorizationIntent.UPDATED)
                .withAuthorizationKey(authorizationKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToUpdateAuthorizationWithUser() {
    // given
    final var user = createUser();
    addAuthorizationToUser(user, AuthorizationResourceType.AUTHORIZATION, PermissionType.UPDATE);
    final var authorizationKey =
        engine
            .authorization()
            .newAuthorization()
            .withOwnerId(user.getUsername())
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceType(AuthorizationResourceType.RESOURCE)
            .withResourceId("*")
            .withPermissions(PermissionType.CREATE)
            .create(DEFAULT_USER.getUsername())
            .getValue()
            .getAuthorizationKey();

    // when
    engine
        .authorization()
        .updateAuthorization(authorizationKey)
        .withPermissions(PermissionType.DELETE_FORM)
        .update(user.getUsername());

    // then
    assertThat(
            RecordingExporter.authorizationRecords(AuthorizationIntent.UPDATED)
                .withAuthorizationKey(authorizationKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeForbiddenToUpdateAuthorizationIfNoPermissions() {
    // given
    final var user = createUser();
    final var authorizationKey =
        engine
            .authorization()
            .newAuthorization()
            .withOwnerId(user.getUsername())
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceType(AuthorizationResourceType.RESOURCE)
            .withResourceId("*")
            .withPermissions(PermissionType.CREATE)
            .create(DEFAULT_USER.getUsername())
            .getValue()
            .getAuthorizationKey();

    // when
    final var rejection =
        engine
            .authorization()
            .updateAuthorization(authorizationKey)
            .withPermissions(PermissionType.DELETE_FORM)
            .expectRejection()
            .update(user.getUsername());

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

  private void addAuthorizationToUser(
      final UserRecordValue user,
      final AuthorizationResourceType authorization,
      final PermissionType permissionType) {
    engine
        .authorization()
        .newAuthorization()
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(authorization)
        .withResourceId("*")
        .withPermissions(permissionType)
        .create(DEFAULT_USER.getUsername());
  }
}
