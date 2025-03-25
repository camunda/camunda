/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.role;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class RoleTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateRole() {
    final var name = UUID.randomUUID().toString();
    final var roleRecord = engine.role().newRole(name).create();

    final var createdRole = roleRecord.getValue();
    Assertions.assertThat(createdRole).isNotNull().hasFieldOrPropertyWithValue("name", name);
  }

  @Test
  @Ignore("Re-enable in https://github.com/camunda/camunda/issues/30109")
  public void shouldNotDuplicate() {
    // given
    final var name = UUID.randomUUID().toString();
    final var roleRecord = engine.role().newRole(name).create();

    // when
    final var duplicatedRoleRecord = engine.role().newRole(name).expectRejection().create();

    final var createdRole = roleRecord.getValue();
    Assertions.assertThat(createdRole).isNotNull().hasFieldOrPropertyWithValue("name", name);

    assertThat(duplicatedRoleRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to create role with name '"
                + name
                + "', but a role with this name already exists");
  }

  @Test
  public void shouldUpdateRole() {
    // given
    final var name = UUID.randomUUID().toString();
    final var createdRecord = engine.role().newRole(name).create();

    // when
    final var newName = UUID.randomUUID().toString();
    final var updatedRoleRecord =
        engine.role().updateRole(createdRecord.getValue().getRoleKey()).withName(newName).update();

    final var updatedRole = updatedRoleRecord.getValue();
    Assertions.assertThat(updatedRole).isNotNull().hasFieldOrPropertyWithValue("name", newName);
  }

  @Test
  public void shouldRejectIfRoleIsNotPresent() {
    // given
    final var name = UUID.randomUUID().toString();
    final var roleRecord = engine.role().newRole(name).create();

    // when
    final var notPresentRoleKey = 1L;
    final var notPresentUpdateRecord =
        engine.role().updateRole(notPresentRoleKey).expectRejection().update();

    final var createdRole = roleRecord.getValue();
    Assertions.assertThat(createdRole).isNotNull().hasFieldOrPropertyWithValue("name", name);

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update role with key '"
                + notPresentRoleKey
                + "', but a role with this key does not exist.");
  }

  @Test
  public void shouldAddEntityToRole() {
    final var userKey =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getKey();
    final var name = UUID.randomUUID().toString();
    final var roleKey = engine.role().newRole(name).create().getValue().getRoleKey();
    final var updatedRole =
        engine
            .role()
            .addEntity(roleKey)
            .withEntityKey(userKey)
            .withEntityType(EntityType.USER)
            .add()
            .getValue();

    Assertions.assertThat(updatedRole)
        .isNotNull()
        .hasFieldOrPropertyWithValue("entityKey", userKey)
        .hasFieldOrPropertyWithValue("entityType", EntityType.USER);
  }

  @Test
  public void shouldRejectIfRoleIsNotPresentWhileAddingEntity() {
    // given
    final var name = UUID.randomUUID().toString();
    final var roleRecord = engine.role().newRole(name).create();

    // when
    final var notPresentRoleKey = 1L;
    final var notPresentUpdateRecord =
        engine.role().addEntity(notPresentRoleKey).expectRejection().add();

    final var createdRole = roleRecord.getValue();
    Assertions.assertThat(createdRole).isNotNull().hasFieldOrPropertyWithValue("name", name);

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update role with key '"
                + notPresentRoleKey
                + "', but a role with this key does not exist.");
  }

  @Test
  public void shouldRejectIfEntityIsNotPresent() {
    // given
    final var name = UUID.randomUUID().toString();
    final var roleRecord = engine.role().newRole(name).create();

    // when
    final var createdRole = roleRecord.getValue();
    final var roleKey = createdRole.getRoleKey();
    final var notPresentUpdateRecord =
        engine
            .role()
            .addEntity(roleKey)
            .withEntityKey(1L)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .add();

    Assertions.assertThat(createdRole).isNotNull().hasFieldOrPropertyWithValue("name", name);

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to add an entity with key '%s' and type '%s' to role with key '%s', but the entity doesn't exist."
                .formatted(1L, EntityType.USER, roleKey));
  }

  @Test
  public void shouldRejectIfEntityIsAlreadyAssigned() {
    // given
    final var name = UUID.randomUUID().toString();
    final var roleRecord = engine.role().newRole(name).create();
    final var roleKey = roleRecord.getValue().getRoleKey();
    final var userKey =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getKey();
    engine.role().addEntity(roleKey).withEntityKey(userKey).withEntityType(EntityType.USER).add();

    // when
    final var notPresentUpdateRecord =
        engine
            .role()
            .addEntity(roleKey)
            .withEntityKey(userKey)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .add();

    // then
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to add entity with key '%d' to role with key '%d', but the entity is already assigned to this role."
                .formatted(userKey, roleKey));
  }

  @Test
  public void shouldRemoveEntityToRole() {
    final var userKey =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getKey();
    final var name = UUID.randomUUID().toString();
    final var roleKey = engine.role().newRole(name).create().getValue().getRoleKey();
    engine.role().addEntity(roleKey).withEntityKey(userKey).withEntityType(EntityType.USER).add();
    final var removedEntity =
        engine
            .role()
            .removeEntity(roleKey)
            .withEntityKey(userKey)
            .withEntityType(EntityType.USER)
            .remove()
            .getValue();

    Assertions.assertThat(removedEntity)
        .isNotNull()
        .hasFieldOrPropertyWithValue("roleKey", roleKey)
        .hasFieldOrPropertyWithValue("entityKey", userKey)
        .hasFieldOrPropertyWithValue("entityType", EntityType.USER);
  }

  @Test
  public void shouldRejectIfRoleIsNotPresentEntityRemoval() {
    // given
    final var name = UUID.randomUUID().toString();
    final var roleRecord = engine.role().newRole(name).create();

    // when
    final var notPresentRoleKey = 1L;
    final var notPresentUpdateRecord =
        engine.role().addEntity(notPresentRoleKey).expectRejection().add();

    final var createdRole = roleRecord.getValue();
    Assertions.assertThat(createdRole).isNotNull().hasFieldOrPropertyWithValue("name", name);

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update role with key '"
                + notPresentRoleKey
                + "', but a role with this key does not exist.");
  }

  @Test
  public void shouldRejectIfEntityIsNotPresentEntityRemoval() {
    // given
    final var name = UUID.randomUUID().toString();
    final var roleRecord = engine.role().newRole(name).create();

    // when
    final var createdRole = roleRecord.getValue();
    final var roleKey = createdRole.getRoleKey();
    final var notPresentUpdateRecord =
        engine
            .role()
            .removeEntity(roleKey)
            .withEntityKey(1L)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .remove();

    Assertions.assertThat(createdRole).isNotNull().hasFieldOrPropertyWithValue("name", name);

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove an entity with key '%s' and type '%s' from role with key '%s', but the entity doesn't exist."
                .formatted(1L, EntityType.USER, roleKey));
  }

  @Test
  public void shouldDeleteRole() {
    // given
    final var name = UUID.randomUUID().toString();
    final var roleKey = engine.role().newRole(name).create().getValue().getRoleKey();
    // when
    final var deletedRole = engine.role().deleteRole(roleKey).delete().getValue();

    Assertions.assertThat(deletedRole).isNotNull().hasFieldOrPropertyWithValue("roleKey", roleKey);
  }

  @Test
  public void shouldRejectIfRoleIsNotPresentOnDeletion() {
    // when
    final var notPresentRoleKey = 1L;
    final var notPresentUpdateRecord =
        engine.role().deleteRole(notPresentRoleKey).expectRejection().delete();

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to delete role with key '%s', but a role with this key doesn't exist."
                .formatted(notPresentRoleKey));
  }
}
