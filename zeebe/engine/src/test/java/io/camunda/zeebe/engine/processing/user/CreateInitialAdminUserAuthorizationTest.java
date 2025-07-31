/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class CreateInitialAdminUserAuthorizationTest {
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

  @Before
  public void beforeEach() {
    // Remove the default user from the admin role so we can properly test without running into
    // other rejections.
    final var dummyAdminRoleId = "dummyAdmin";
    engine.role().newRole(dummyAdminRoleId).withName(dummyAdminRoleId).create();
    engine
        .role()
        .addEntity(dummyAdminRoleId)
        .withEntityId(DEFAULT_USER.getUsername())
        .withEntityType(EntityType.USER);
    engine
        .authorization()
        .newAuthorization()
        .withOwnerId(dummyAdminRoleId)
        .withOwnerType(AuthorizationOwnerType.ROLE)
        .withResourceType(AuthorizationResourceType.AUTHORIZATION)
        .withPermissions(PermissionType.CREATE)
        .withResourceMatcher(WILDCARD.getMatcher())
        .withResourceId(WILDCARD.getResourceId())
        .create(DEFAULT_USER.getUsername());
    engine
        .authorization()
        .newAuthorization()
        .withOwnerId(dummyAdminRoleId)
        .withOwnerType(AuthorizationOwnerType.ROLE)
        .withResourceType(AuthorizationResourceType.USER)
        .withPermissions(PermissionType.CREATE)
        .withResourceMatcher(WILDCARD.getMatcher())
        .withResourceId(WILDCARD.getResourceId())
        .create(DEFAULT_USER.getUsername());
    engine
        .authorization()
        .newAuthorization()
        .withOwnerId(dummyAdminRoleId)
        .withOwnerType(AuthorizationOwnerType.ROLE)
        .withResourceType(AuthorizationResourceType.ROLE)
        .withPermissions(PermissionType.UPDATE)
        .withResourceMatcher(WILDCARD.getMatcher())
        .withResourceId(WILDCARD.getResourceId())
        .create(DEFAULT_USER.getUsername());
    engine
        .role()
        .addEntity(dummyAdminRoleId)
        .withEntityId(DEFAULT_USER.getUsername())
        .withEntityType(EntityType.USER)
        .add();
    engine
        .role()
        .removeEntity("admin")
        .withEntityId(DEFAULT_USER.getUsername())
        .withEntityType(EntityType.USER)
        .remove();
  }

  @Test
  public void shouldBeAuthorizedToCreateInitialAdminUser() {
    // when
    final var user =
        engine
            .user()
            .newInitialAdminUser(UUID.randomUUID().toString())
            .withPassword(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .withEmail(UUID.randomUUID().toString())
            .create(DEFAULT_USER.getUsername())
            .getValue();

    // then
    assertThat(
            RecordingExporter.userRecords(UserIntent.CREATED)
                .withUsername(user.getUsername())
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.roleRecords(RoleIntent.ENTITY_ADDED)
                .withEntityId(user.getUsername())
                .withEntityType(EntityType.USER)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnAuthorizedToCreateInitialAdminUserWithoutUserCreatePermissions() {
    // given
    final var unauthorizedUser = createUser(DEFAULT_USER.getUsername());
    addPermissionsToUser(unauthorizedUser, AuthorizationResourceType.ROLE, PermissionType.UPDATE);

    // when
    final var rejection =
        engine
            .user()
            .newInitialAdminUser(UUID.randomUUID().toString())
            .withPassword(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .withEmail(UUID.randomUUID().toString())
            .expectRejection()
            .create(unauthorizedUser.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'CREATE' on resource 'USER'");
  }

  @Test
  public void shouldBeUnAuthorizedToCreateInitialAdminUserWithoutRoleUpdatePermissions() {
    // given
    final var unauthorizedUser = createUser(DEFAULT_USER.getUsername());
    addPermissionsToUser(unauthorizedUser, AuthorizationResourceType.USER, PermissionType.CREATE);

    // when
    final var rejection =
        engine
            .user()
            .newInitialAdminUser(UUID.randomUUID().toString())
            .withPassword(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .withEmail(UUID.randomUUID().toString())
            .expectRejection()
            .create(unauthorizedUser.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'UPDATE' on resource 'ROLE'");
  }

  private UserRecordValue createUser(final String authorizedUsername) {
    return engine
        .user()
        .newUser(UUID.randomUUID().toString())
        .withPassword(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .withEmail(UUID.randomUUID().toString())
        .create(authorizedUsername)
        .getValue();
  }

  private void addPermissionsToUser(
      final UserRecordValue user,
      final AuthorizationResourceType authorization,
      final PermissionType permissionType) {
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(permissionType)
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(authorization)
        .withResourceMatcher(WILDCARD.getMatcher())
        .withResourceId(WILDCARD.getResourceId())
        .create(DEFAULT_USER.getUsername());
  }
}
