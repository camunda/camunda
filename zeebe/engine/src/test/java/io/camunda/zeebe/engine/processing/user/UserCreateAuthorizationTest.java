/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.UUID;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class UserCreateAuthorizationTest {

  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true))
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)));

  private static long defaultUserKey = -1L;
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @BeforeClass
  public static void beforeAll() {
    defaultUserKey =
        RecordingExporter.userRecords(UserIntent.CREATED)
            .withUsername(DEFAULT_USER.getUsername())
            .getFirst()
            .getKey();
  }

  @Test
  public void shouldBeAuthorizedToCreateUserWithDefaultUser() {
    // when
    final long userKey = createUser(defaultUserKey);

    // then
    assertThat(RecordingExporter.userRecords(UserIntent.CREATED).withUserKey(userKey).exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToCreateUserWithPermissions() {
    // given
    final var authorizedUserKey = createUser(defaultUserKey);
    addPermissionsToUser(authorizedUserKey, AuthorizationResourceType.USER, PermissionType.CREATE);

    // when
    final var userKey = createUser(authorizedUserKey);

    // then
    assertThat(RecordingExporter.userRecords(UserIntent.CREATED).withUserKey(userKey).exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnAuthorizedToCreateUserWithoutPermissions() {
    // given
    final var unauthorizedUserKey = createUser(defaultUserKey);

    // when
    final var rejection =
        ENGINE
            .user()
            .newUser(UUID.randomUUID().toString())
            .withPassword(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .withEmail(UUID.randomUUID().toString())
            .expectRejection()
            .create(unauthorizedUserKey);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'CREATE' on resource 'USER'");
  }

  private static long createUser(final long authorizedUserKey) {
    return ENGINE
        .user()
        .newUser(UUID.randomUUID().toString())
        .withPassword(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .withEmail(UUID.randomUUID().toString())
        .create(authorizedUserKey)
        .getKey();
  }

  private void addPermissionsToUser(
      final long userKey,
      final AuthorizationResourceType authorization,
      final PermissionType permissionType) {
    ENGINE
        .authorization()
        .permission()
        .withOwnerKey(userKey)
        .withResourceType(authorization)
        .withPermission(permissionType, "*")
        .add(defaultUserKey);
  }
}
