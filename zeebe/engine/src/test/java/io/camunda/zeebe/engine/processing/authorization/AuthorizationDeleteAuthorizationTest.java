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
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class AuthorizationDeleteAuthorizationTest {

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
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)))
          .withSecurityConfig(
              cfg ->
                  cfg.getInitialization()
                      .getDefaultRoles()
                      .put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername()))));

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldBeAuthorizedToDeleteAuthorizationWithDefaultUser() {
    // given
    final var resourceId = "resourceId";
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var user = createUser();
    final var authorizationKey =
        addAuthorizationToUser(user, resourceType, permissionType, resourceId);

    // when
    engine.authorization().deleteAuthorization(authorizationKey).delete(DEFAULT_USER.getUsername());

    // when
    assertThat(
            RecordingExporter.authorizationRecords(AuthorizationIntent.DELETED)
                .withAuthorizationKey(authorizationKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToDeleteAuthorizationWithUser() {
    // given
    final var resourceType = AuthorizationResourceType.AUTHORIZATION;
    final var permissionType = PermissionType.DELETE;
    final var user = createUser();
    final var authorizationKey = addAuthorizationToUser(user, resourceType, permissionType, "*");

    // when
    engine.authorization().deleteAuthorization(authorizationKey).delete(user.getUsername());

    // then
    assertThat(
            RecordingExporter.authorizationRecords(AuthorizationIntent.DELETED)
                .withAuthorizationKey(authorizationKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToDeleteAuthorizationWithNoPermission() {
    // given
    final var resourceType = AuthorizationResourceType.AUTHORIZATION;
    final var permissionType = PermissionType.CREATE;
    final var user = createUser();
    final var authorizationKey = addAuthorizationToUser(user, resourceType, permissionType, "*");

    // when
    final var rejection =
        engine
            .authorization()
            .deleteAuthorization(authorizationKey)
            .expectRejection()
            .delete(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'DELETE' on resource 'AUTHORIZATION'");
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

  private long addAuthorizationToUser(
      final UserRecordValue user,
      final AuthorizationResourceType authorization,
      final PermissionType permissionType,
      final String resourceId) {
    final var resourceMatcher =
        AuthorizationScope.WILDCARD_CHAR.equals(resourceId)
            ? AuthorizationResourceMatcher.ANY
            : AuthorizationResourceMatcher.ID;
    return engine
        .authorization()
        .newAuthorization()
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(authorization)
        .withResourceMatcher(resourceMatcher)
        .withResourceId(resourceId)
        .withPermissions(permissionType)
        .create(DEFAULT_USER.getUsername())
        .getValue()
        .getAuthorizationKey();
  }
}
