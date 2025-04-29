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
import io.camunda.zeebe.test.util.Strings;
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
    final var id = UUID.randomUUID().toString();
    final var name = "name";
    final var description = "description";
    final var roleRecord =
        engine.role().newRole(id).withName(name).withDescription(description).create();

    final var createdRole = roleRecord.getValue();
    assertThat(createdRole).hasRoleId(id).hasName(name).hasDescription(description);
  }

  @Test
  @Ignore("Re-enable in https://github.com/camunda/camunda/issues/30109")
  public void shouldNotDuplicate() {
    // given
    final var id = UUID.randomUUID().toString();
    final var roleRecord = engine.role().newRole(id).create();

    // when
    final var duplicatedRoleRecord = engine.role().newRole(id).expectRejection().create();

    final var createdRole = roleRecord.getValue();
    Assertions.assertThat(createdRole).isNotNull().hasFieldOrPropertyWithValue("roleId", id);

    assertThat(duplicatedRoleRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to create role with ID '%s', but a role with this ID already exists"
                .formatted(id));
  }

  @Test
  public void shouldUpdateRole() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var createdRecord =
        engine
            .role()
            .newRole(roleId)
            .withName(UUID.randomUUID().toString())
            .withDescription(UUID.randomUUID().toString())
            .create();

    // when
    final var updatedName = UUID.randomUUID().toString();
    final var updatedDescription = UUID.randomUUID().toString();
    final var updatedRole =
        engine
            .role()
            .updateRole(roleId)
            .withName(updatedName)
            .withDescription(updatedDescription)
            .update()
            .getValue();

    assertThat(updatedRole)
        .isNotNull()
        .hasRoleId(roleId)
        .hasName(updatedName)
        .hasDescription(updatedDescription);
  }

  @Test
  public void shouldNotUpdateRoleKey() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var createdRecord = engine.role().newRole(roleId).create();

    // when
    final var updatedKey = 111L;
    final var updatedRole =
        engine.role().updateRole(roleId).withRoleKey(updatedKey).update().getValue();

    assertThat(updatedRole).isNotNull().hasRoleId(roleId).hasRoleKey(createdRecord.getKey());
  }

  @Test
  public void shouldRejectIfRoleIsNotPresent() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var roleRecord = engine.role().newRole(roleId).create();

    // when
    final var notPresentRoleId = Strings.newRandomValidIdentityId();
    final var notPresentUpdateRecord =
        engine.role().updateRole(notPresentRoleId).expectRejection().update();

    final var createdRole = roleRecord.getValue();
    assertThat(createdRole).isNotNull().hasRoleId(roleId);

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update role with ID '"
                + notPresentRoleId
                + "', but a role with this ID does not exist.");
  }

  @Test
  public void shouldAddEntityToRole() {
    final var username =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getValue()
            .getUsername();
    final var roleId = Strings.newRandomValidIdentityId();
    engine.role().newRole(roleId).create().getValue().getRoleKey();
    final var updatedRole =
        engine
            .role()
            .addEntity(roleId)
            .withEntityId(username)
            .withEntityType(EntityType.USER)
            .add()
            .getValue();

    assertThat(updatedRole)
        .isNotNull()
        .hasRoleId(roleId)
        .hasEntityId(username)
        .hasEntityType(EntityType.USER);
  }

  @Test
  public void shouldRejectIfRoleIsNotPresentWhileAddingEntity() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    engine.role().newRole(roleId).create();

    // when
    final var notPresentRoleId = Strings.newRandomValidIdentityId();
    final var notPresentUpdateRecord =
        engine.role().addEntity(notPresentRoleId).expectRejection().add();

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update role with ID '%s', but a role with this ID does not exist."
                .formatted(notPresentRoleId));
  }

  @Test
  public void shouldRejectIfMappingIsNotPresent() {
    // given
    final var roleId = UUID.randomUUID().toString();
    final var roleRecord = engine.role().newRole(roleId).create();

    // when
    roleRecord.getValue();
    final var entityId = "non-existing-entity";
    final var notPresentUpdateRecord =
        engine
            .role()
            .addEntity(roleId)
            .withEntityId(entityId)
            .withEntityType(EntityType.MAPPING)
            .expectRejection()
            .add();

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to add an entity with ID '%s' and type '%s' to role with ID '%s', but the entity doesn't exist."
                .formatted(entityId, EntityType.MAPPING, roleId));
  }

  @Test
  public void shouldNotRejectIfUserIsNotPresent() {
    // given
    final var roleId = UUID.randomUUID().toString();
    final var roleRecord = engine.role().newRole(roleId).create();

    // when
    roleRecord.getValue();
    final var entityId = "non-existing-entity";
    final var updatedRecord =
        engine
            .role()
            .addEntity(roleId)
            .withEntityId(entityId)
            .withEntityType(EntityType.USER)
            .add()
            .getValue();

    assertThat(updatedRecord).hasEntityId(entityId);
  }

  @Test
  public void shouldRejectIfEntityIsAlreadyAssigned() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    engine.role().newRole(roleId).create();
    final var username =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getValue()
            .getUsername();
    engine.role().addEntity(roleId).withEntityId(username).withEntityType(EntityType.USER).add();

    // when
    final var notPresentUpdateRecord =
        engine
            .role()
            .addEntity(roleId)
            .withEntityId(username)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .add();

    // then
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to add entity with ID '%s' to role with ID '%s', but the entity is already assigned to this role."
                .formatted(username, roleId));
  }

  @Test
  public void shouldRemoveEntityFromRole() {
    final var username = Strings.newRandomValidUsername();
    engine
        .user()
        .newUser(username)
        .withEmail("foo@bar")
        .withName("Foo Bar")
        .withPassword("zabraboof")
        .create();
    final var roleId = UUID.randomUUID().toString();
    engine.role().newRole(roleId).create();
    engine.role().addEntity(roleId).withEntityId(username).withEntityType(EntityType.USER).add();
    final var removedEntity =
        engine
            .role()
            .removeEntity(roleId)
            .withEntityId(username)
            .withEntityType(EntityType.USER)
            .remove()
            .getValue();

    assertThat(removedEntity)
        .isNotNull()
        .hasRoleId(roleId)
        .hasEntityId(username)
        .hasEntityType(EntityType.USER);
  }

  @Test
  public void shouldRejectIfRoleIsNotPresentEntityRemoval() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var roleRecord = engine.role().newRole(roleId).create();

    // when
    final var notPresentRoleId = Strings.newRandomValidIdentityId();
    final var notPresentUpdateRecord =
        engine.role().addEntity(notPresentRoleId).expectRejection().add();

    final var createdRole = roleRecord.getValue();
    assertThat(createdRole).isNotNull().hasRoleId(roleId);

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update role with ID '"
                + notPresentRoleId
                + "', but a role with this ID does not exist.");
  }

  @Test
  public void shouldRejectIfEntityIsNotPresentEntityRemoval() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var roleRecord = engine.role().newRole(roleId).create().getValue();

    // when
    final var entityId = Strings.newRandomValidIdentityId();
    final var notPresentUpdateRecord =
        engine
            .role()
            .removeEntity(roleId)
            .withEntityId(entityId)
            .withEntityType(EntityType.MAPPING)
            .expectRejection()
            .remove();

    assertThat(roleRecord).isNotNull().hasRoleId(roleId);

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove an entity with ID '%s' and type '%s' from role with ID '%s', but the entity doesn't exist."
                .formatted(entityId, EntityType.MAPPING, roleId));
  }

  @Test
  public void shouldDeleteRole() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var name = UUID.randomUUID().toString();
    engine.role().newRole(roleId).withName(name).create();

    // when
    final var deletedRole = engine.role().deleteRole(roleId).delete().getValue();
    assertThat(deletedRole).hasRoleId(roleId).hasName(name);
  }

  @Test
  public void shouldRejectIfRoleIsNotPresentOnDeletion() {
    // when
    final var notPresentRoleId = Strings.newRandomValidIdentityId();
    final var notPresentUpdateRecord =
        engine.role().deleteRole(notPresentRoleId).expectRejection().delete();

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to delete role with ID '%s', but a role with this ID doesn't exist."
                .formatted(notPresentRoleId));
  }
}
