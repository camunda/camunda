/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import static io.camunda.zeebe.engine.common.processing.user.UserCreateInitialAdminProcessor.ADMIN_ROLE_HAS_USERS_ERROR_MESSAGE;
import static io.camunda.zeebe.engine.common.processing.user.UserCreateInitialAdminProcessor.ADMIN_ROLE_NOT_FOUND_ERROR_MESSAGE;
import static io.camunda.zeebe.engine.common.processing.user.UserCreateInitialAdminProcessor.USER_ALREADY_EXISTS_ERROR_MESSAGE;
import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.DefaultRole;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class CreateInitialAdminUserTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher testWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateAdminUser() {
    // given
    // Create the admin role manually. Normally starting the broker does this, but it needs to be
    // explicitly enabled in tests. If we'd do that here we won't be able to test the rejection if
    // the role does not exist in this test class.
    final var adminRoleId = DefaultRole.ADMIN.getId();
    engine.role().newRole(adminRoleId).withName(adminRoleId).create();
    final var username = UUID.randomUUID().toString();

    // when
    final var createdAdminUser =
        engine
            .user()
            .newInitialAdminUser(username)
            .withName("name")
            .withEmail("email")
            .withPassword("password")
            .create()
            .getValue();

    // then
    assertThat(createdAdminUser)
        .hasUsername(username)
        .hasName("name")
        .hasEmail("email")
        .hasPassword("password");
    assertThat(
            RecordingExporter.userRecords(UserIntent.CREATED)
                .withUsername(username)
                .getFirst()
                .getValue())
        .hasUsername(username)
        .hasName("name")
        .hasEmail("email")
        .hasPassword("password");
    Assertions.assertThat(
            RecordingExporter.roleRecords(RoleIntent.ADD_ENTITY)
                .withRoleId(adminRoleId)
                .withEntityId(username)
                .withEntityType(EntityType.USER)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldRejectIfUserAlreadyExists() {
    // given
    // Create the admin role manually. Normally starting the broker does this, but it needs to be
    // explicitly enabled in tests. If we'd do that here we won't be able to test the rejection if
    // the role does not exist in this test class.
    final var adminRoleId = DefaultRole.ADMIN.getId();
    engine.role().newRole(adminRoleId).withName(adminRoleId).create();
    final var username = UUID.randomUUID().toString();
    engine
        .user()
        .newUser(username)
        .withName("name")
        .withEmail("email")
        .withPassword("password")
        .create();

    // when
    final var rejection =
        engine
            .user()
            .newInitialAdminUser(username)
            .withName("Bar Foo")
            .withEmail("bar@foo.com")
            .withPassword("password")
            .expectRejection()
            .create();

    assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(USER_ALREADY_EXISTS_ERROR_MESSAGE.formatted(username));
  }

  @Test
  public void shouldRejectIfRoleDoesNotExist() {
    // given
    final var username = UUID.randomUUID().toString();

    // when
    final var rejection =
        engine
            .user()
            .newInitialAdminUser(username)
            .withName("name")
            .withEmail("email")
            .withPassword("password")
            .expectRejection()
            .create();

    assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            ADMIN_ROLE_NOT_FOUND_ERROR_MESSAGE.formatted(DefaultRole.ADMIN.getId()));
  }

  @Test
  public void shouldRejectIfAdminRoleAlreadyHasUsers() {
    // given
    final var adminRoleId = DefaultRole.ADMIN.getId();
    engine.role().newRole(adminRoleId).withName(adminRoleId).create();
    final var username = UUID.randomUUID().toString();
    engine
        .user()
        .newUser(username)
        .withName("name")
        .withEmail("email")
        .withPassword("password")
        .create();
    engine
        .role()
        .addEntity(adminRoleId)
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();

    // when
    final var rejection =
        engine
            .user()
            .newInitialAdminUser(UUID.randomUUID().toString())
            .withName("name")
            .withEmail("email")
            .withPassword("password")
            .expectRejection()
            .create();

    assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(ADMIN_ROLE_HAS_USERS_ERROR_MESSAGE.formatted(adminRoleId));
  }
}
