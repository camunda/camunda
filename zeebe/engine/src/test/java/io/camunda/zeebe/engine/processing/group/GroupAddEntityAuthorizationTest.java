/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.group;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class GroupAddEntityAuthorizationTest {
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
          .withSecurityConfig(
              cfg -> {
                cfg.getAuthorizations().setEnabled(true);
                cfg.getInitialization().setUsers(List.of(DEFAULT_USER));
                cfg.getInitialization()
                    .getDefaultRoles()
                    .put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername())));
              });

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldBeAuthorizedToAddEntityToGroupWithScopedPermission() {
    // given
    final var allowedGroupId = Strings.newRandomValidIdentityId();
    final var user = createUser();
    final var member = createUser();
    engine.group().newGroup(allowedGroupId).withName("allowed").create(DEFAULT_USER.getUsername());
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(PermissionType.UPDATE)
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(AuthorizationResourceType.GROUP)
        .withResourceMatcher(AuthorizationResourceMatcher.ID)
        .withResourceId(allowedGroupId)
        .create(DEFAULT_USER.getUsername());

    // when - a member is added to the specifically-granted group
    engine
        .group()
        .addEntity(allowedGroupId)
        .withEntityId(member.getUsername())
        .withEntityType(EntityType.USER)
        .add(user.getUsername());

    // then
    assertThat(
            RecordingExporter.groupRecords(GroupIntent.ENTITY_ADDED)
                .withGroupId(allowedGroupId)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToAddEntityToGroupWithScopedPermissionOnDifferentGroup() {
    // given
    final var allowedGroupId = Strings.newRandomValidIdentityId();
    final var deniedGroupId = Strings.newRandomValidIdentityId();
    final var user = createUser();
    final var member = createUser();
    engine.group().newGroup(allowedGroupId).withName("allowed").create(DEFAULT_USER.getUsername());
    engine.group().newGroup(deniedGroupId).withName("denied").create(DEFAULT_USER.getUsername());
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(PermissionType.UPDATE)
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(AuthorizationResourceType.GROUP)
        .withResourceMatcher(AuthorizationResourceMatcher.ID)
        .withResourceId(allowedGroupId)
        .create(DEFAULT_USER.getUsername());

    // when - adding a member to a different group ID is rejected
    final var rejection =
        engine
            .group()
            .addEntity(deniedGroupId)
            .withEntityId(member.getUsername())
            .withEntityType(EntityType.USER)
            .expectRejection()
            .add(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'UPDATE' on resource 'GROUP', required resource identifiers are one of '[*, %s]'"
                .formatted(deniedGroupId));
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
}
