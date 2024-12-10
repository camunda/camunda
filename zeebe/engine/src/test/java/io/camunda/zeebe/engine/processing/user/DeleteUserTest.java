/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class DeleteUserTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldDeleteAUser() {
    final var username = UUID.randomUUID().toString();
    final var userRecord =
        ENGINE
            .user()
            .newUser(username)
            .withName("Foo Bar")
            .withEmail("foo@bar.com")
            .withPassword("password")
            .create();

    final var deletedUser =
        ENGINE.user().deleteUser(userRecord.getKey()).withUsername("").delete().getValue();

    assertThat(deletedUser).isNotNull().hasFieldOrPropertyWithValue("userKey", userRecord.getKey());
  }

  @Test
  public void shouldCleanupMembership() {
    final var username = UUID.randomUUID().toString();
    final var userRecord =
        ENGINE
            .user()
            .newUser(username)
            .withName("Foo Bar")
            .withEmail("foo@bar.com")
            .withPassword("password")
            .create();
    final var group = ENGINE.group().newGroup("group").create();
    final var role = ENGINE.role().newRole("role").create();
    final var tenant = ENGINE.tenant().newTenant().withTenantId("tenant").create();

    ENGINE
        .group()
        .addEntity(group.getKey())
        .withEntityKey(userRecord.getKey())
        .withEntityType(EntityType.USER)
        .add();
    ENGINE
        .role()
        .addEntity(role.getKey())
        .withEntityKey(userRecord.getKey())
        .withEntityType(EntityType.USER)
        .add();
    ENGINE
        .tenant()
        .addEntity(tenant.getKey())
        .withEntityKey(userRecord.getKey())
        .withEntityType(EntityType.USER)
        .add();

    // when
    ENGINE.user().deleteUser(userRecord.getKey()).withUsername("").delete();

    // then
    Assertions.assertThat(
            RecordingExporter.groupRecords(GroupIntent.ENTITY_REMOVED)
                .withGroupKey(group.getKey())
                .withEntityKey(userRecord.getKey())
                .exists())
        .isTrue();
    Assertions.assertThat(
            RecordingExporter.roleRecords(RoleIntent.ENTITY_REMOVED)
                .withRoleKey(role.getKey())
                .withEntityKey(userRecord.getKey())
                .exists())
        .isTrue();
    Assertions.assertThat(
            RecordingExporter.tenantRecords(TenantIntent.ENTITY_REMOVED)
                .withTenantKey(tenant.getKey())
                .withEntityKey(userRecord.getKey())
                .exists())
        .isTrue();
  }

  @Test
  public void shouldRejectTheCommandIfUserDoesNotExist() {
    final var userNotFoundRejection =
        ENGINE
            .user()
            .deleteUser(1234L)
            .withUsername("foobar")
            .withName("Bar Foo")
            .withEmail("foo@bar.blah")
            .withPassword("Foo Bar")
            .expectRejection()
            .delete();

    assertThat(userNotFoundRejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to delete user with key 1234, but a user with this key does not exist");
  }
}
