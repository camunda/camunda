/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.group;

import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class GroupDeleteAuthorizationTest {
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
  public void shouldRevokePermissionsOfDeletedGroupAlthoughAuthorizationWasCached() {
    // given a user that may create roles only through its group's role
    final var user = createUser();
    final var groupId = Strings.newRandomValidIdentityId();
    final var roleId = Strings.newRandomValidIdentityId();
    engine.group().newGroup(groupId).withName("group").create();
    engine.role().newRole(roleId).create();
    grantRoleCreateTo(roleId);
    engine.role().addEntity(roleId).withEntityId(groupId).withEntityType(EntityType.GROUP).add();
    engine
        .group()
        .addEntity(groupId)
        .withEntityId(user.getUsername())
        .withEntityType(EntityType.USER)
        .add();
    // the check caches the authorization result
    engine.role().newRole(Strings.newRandomValidIdentityId()).create(user.getUsername());

    // when
    engine.group().deleteGroup(groupId).delete();

    // then the user no longer has the role through the deleted group
    final var rejection =
        engine
            .role()
            .newRole(Strings.newRandomValidIdentityId())
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

  /** Lets the role create roles, a permission that the user then holds only through the role. */
  private void grantRoleCreateTo(final String roleId) {
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(PermissionType.CREATE)
        .withOwnerId(roleId)
        .withOwnerType(AuthorizationOwnerType.ROLE)
        .withResourceType(AuthorizationResourceType.ROLE)
        .withResourceMatcher(WILDCARD.getMatcher())
        .withResourceId(WILDCARD.getResourceId())
        .create();
  }
}
