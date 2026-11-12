/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.role;

import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

@NullMarked
public class RoleDeleteAuthorizationTest {
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
          .withAuthorizationsEnabled(true)
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)))
          .withSecurityConfig(
              cfg -> {
                final var defaultRoles = new HashMap<>(cfg.getInitialization().getDefaultRoles());
                defaultRoles.put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername())));
                cfg.getInitialization().setDefaultRoles(defaultRoles);
              });

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldBeAuthorizedToDeleteRoleWithScopedPermission() {
    // given
    final var allowedRoleId = Strings.newRandomValidIdentityId();
    final var deniedRoleId = Strings.newRandomValidIdentityId();
    final var user = createUser();
    engine.role().newRole(allowedRoleId).withName("allowed").create(DEFAULT_USER.getUsername());
    engine.role().newRole(deniedRoleId).withName("denied").create(DEFAULT_USER.getUsername());
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(PermissionType.DELETE)
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(AuthorizationResourceType.ROLE)
        .withResourceMatcher(AuthorizationResourceMatcher.ID)
        .withResourceId(allowedRoleId)
        .create(DEFAULT_USER.getUsername());

    // when - the specifically-granted role is deleted
    engine.role().deleteRole(allowedRoleId).delete(user.getUsername());

    // then
    assertThat(RecordingExporter.roleRecords(RoleIntent.DELETED).withRoleId(allowedRoleId).exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToDeleteRoleWithScopedPermissionOnDifferentRole() {
    // given
    final var allowedRoleId = Strings.newRandomValidIdentityId();
    final var deniedRoleId = Strings.newRandomValidIdentityId();
    final var user = createUser();
    engine.role().newRole(allowedRoleId).withName("allowed").create(DEFAULT_USER.getUsername());
    engine.role().newRole(deniedRoleId).withName("denied").create(DEFAULT_USER.getUsername());
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(PermissionType.DELETE)
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(AuthorizationResourceType.ROLE)
        .withResourceMatcher(AuthorizationResourceMatcher.ID)
        .withResourceId(allowedRoleId)
        .create(DEFAULT_USER.getUsername());

    // when - a different role ID is rejected
    final var rejection =
        engine.role().deleteRole(deniedRoleId).expectRejection().delete(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'DELETE' on resource 'ROLE'");
  }

  @Test
  public void shouldRevokePermissionsOfDeletedRoleAlthoughMembershipsWereCached() {
    // given a user that may create groups only through its role
    final var user = createUser();
    final var roleId = Strings.newRandomValidIdentityId();
    engine.role().newRole(roleId).create(DEFAULT_USER.getUsername());
    grantGroupCreateTo(roleId);
    engine
        .role()
        .addEntity(roleId)
        .withEntityId(user.getUsername())
        .withEntityType(EntityType.USER)
        .add(DEFAULT_USER.getUsername());
    // the check caches the user's memberships
    engine
        .group()
        .newGroup(Strings.newRandomValidIdentityId())
        .withName("allowed")
        .create(user.getUsername());

    // when
    engine.role().deleteRole(roleId).delete(DEFAULT_USER.getUsername());

    // then the user no longer has the deleted role
    final var rejection =
        engine
            .group()
            .newGroup(Strings.newRandomValidIdentityId())
            .withName("denied")
            .expectRejection()
            .create(user.getUsername());
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.FORBIDDEN);
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

  /** Lets the role create groups, a permission that the user then holds only through the role. */
  private void grantGroupCreateTo(final String roleId) {
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(PermissionType.CREATE)
        .withOwnerId(roleId)
        .withOwnerType(AuthorizationOwnerType.ROLE)
        .withResourceType(AuthorizationResourceType.GROUP)
        .withResourceMatcher(WILDCARD.getMatcher())
        .withResourceId(WILDCARD.getResourceId())
        .create(DEFAULT_USER.getUsername());
  }
}
