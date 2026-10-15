/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.group;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class GroupSetEntitiesAuthorizationTest {
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
  public void shouldBeAuthorizedWithUpdatePermissionOnGroup() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var user = createUser();
    final var member = createUser();
    engine.group().newGroup(groupId).withName("group").create(DEFAULT_USER.getUsername());
    grantUpdate(user, groupId);

    // when
    engine
        .group()
        .setEntities(groupId)
        .withEntityType(EntityType.USER)
        .withEntityIds(List.of(member.getUsername()))
        .set(user.getUsername());

    // then
    assertThat(
            RecordingExporter.groupRecords(GroupIntent.ENTITY_ADDED).withGroupId(groupId).exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedWithoutPermissionOnGroup() {
    // given
    final var allowedGroupId = Strings.newRandomValidIdentityId();
    final var deniedGroupId = Strings.newRandomValidIdentityId();
    final var user = createUser();
    final var member = createUser();
    engine.group().newGroup(allowedGroupId).withName("allowed").create(DEFAULT_USER.getUsername());
    engine.group().newGroup(deniedGroupId).withName("denied").create(DEFAULT_USER.getUsername());
    grantUpdate(user, allowedGroupId);

    // when
    final var rejection =
        engine
            .group()
            .setEntities(deniedGroupId)
            .withEntityType(EntityType.USER)
            .withEntityIds(List.of(member.getUsername()))
            .expectRejection()
            .set(user.getUsername());

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.FORBIDDEN);
  }

  private void grantUpdate(final UserRecordValue user, final String groupId) {
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(PermissionType.UPDATE)
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(AuthorizationResourceType.GROUP)
        .withResourceMatcher(AuthorizationResourceMatcher.ID)
        .withResourceId(groupId)
        .create(DEFAULT_USER.getUsername());
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
