/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.authorization;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.RoleState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionAction;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class RoleTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static long userKey;
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();
  private final RoleState roleState = ENGINE.getProcessingState().getRoleState();
  private final UserState userState = ENGINE.getProcessingState().getUserState();
  private final AuthorizationState authorizationState =
      ENGINE.getProcessingState().getAuthorizationState();

  @BeforeClass
  public static void setUp() {
    userKey =
        ENGINE
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getKey();
  }

  @Test
  public void shouldCreateRole() {
    final var name = UUID.randomUUID().toString();
    final var roleRecord = ENGINE.role().newRole(name).create();

    final var createdRole = roleRecord.getValue();
    Assertions.assertThat(createdRole).isNotNull().hasFieldOrPropertyWithValue("name", name);
    final var ownerType = authorizationState.getOwnerType(createdRole.getRoleKey());
    Assertions.assertThat(ownerType).isPresent().hasValue(AuthorizationOwnerType.ROLE);
  }

  @Test
  public void shouldNotDuplicate() {
    // given
    final var name = UUID.randomUUID().toString();
    final var roleRecord = ENGINE.role().newRole(name).create();

    // when
    final var duplicatedRoleRecord = ENGINE.role().newRole(name).expectRejection().create();

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
    final var createdRecord = ENGINE.role().newRole(name).create();

    // when
    final var newName = UUID.randomUUID().toString();
    final var updatedRoleRecord =
        ENGINE.role().updateRole(createdRecord.getValue().getRoleKey()).withName(newName).update();

    final var updatedRole = updatedRoleRecord.getValue();
    Assertions.assertThat(updatedRole).isNotNull().hasFieldOrPropertyWithValue("name", newName);
  }

  @Test
  public void shouldRejectIfRoleIsNotPresent() {
    // given
    final var name = UUID.randomUUID().toString();
    final var roleRecord = ENGINE.role().newRole(name).create();

    // when
    final var notPresentRoleKey = 1L;
    final var notPresentUpdateRecord =
        ENGINE.role().updateRole(notPresentRoleKey).expectRejection().update();

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
  public void shouldRejectIfRoleWithSameNameIsPresent() {
    // given
    final var name = UUID.randomUUID().toString();
    final var roleKey = ENGINE.role().newRole(name).create().getValue().getRoleKey();
    final var anotherName = UUID.randomUUID().toString();
    ENGINE.role().newRole(anotherName).create();

    // when
    final var notPresentUpdateRecord =
        ENGINE.role().updateRole(roleKey).withName(anotherName).expectRejection().update();

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to update role with name '"
                + anotherName
                + "', but a role with this name already exists");
  }

  @Test
  public void shouldAddEntityToRole() {
    final var name = UUID.randomUUID().toString();
    final var roleKey = ENGINE.role().newRole(name).create().getValue().getRoleKey();
    final var updatedRole =
        ENGINE
            .role()
            .addEntity(roleKey)
            .withEntityKey(userKey)
            .withEntityType(EntityType.USER)
            .add()
            .getValue();

    Assertions.assertThat(updatedRole)
        .isNotNull()
        .hasFieldOrPropertyWithValue("entityKey", userKey);

    final var persistedEntity = roleState.getEntityType(roleKey, userKey);
    assertTrue(persistedEntity.isPresent());
    Assertions.assertThat(persistedEntity.get()).isEqualTo(EntityType.USER);
    final var user = userState.getUser(userKey);
    assertTrue(user.isPresent());
    Assertions.assertThat(user.get().getRoleKeysList()).contains(roleKey);
  }

  @Test
  public void shouldRejectIfRoleIsNotPresentWhileAddingEntity() {
    // given
    final var name = UUID.randomUUID().toString();
    final var roleRecord = ENGINE.role().newRole(name).create();

    // when
    final var notPresentRoleKey = 1L;
    final var notPresentUpdateRecord =
        ENGINE.role().addEntity(notPresentRoleKey).expectRejection().add();

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
    final var roleRecord = ENGINE.role().newRole(name).create();

    // when
    final var createdRole = roleRecord.getValue();
    final var roleKey = createdRole.getRoleKey();
    final var notPresentUpdateRecord =
        ENGINE
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
  public void shouldRemoveEntityToRole() {
    final var name = UUID.randomUUID().toString();
    final var roleKey = ENGINE.role().newRole(name).create().getValue().getRoleKey();
    ENGINE.role().addEntity(roleKey).withEntityKey(userKey).withEntityType(EntityType.USER).add();
    ENGINE
        .role()
        .removeEntity(roleKey)
        .withEntityKey(userKey)
        .withEntityType(EntityType.USER)
        .remove()
        .getValue();

    final var persistedEntity = roleState.getEntityType(roleKey, userKey);
    assertTrue(persistedEntity.isEmpty());
    final var user = userState.getUser(userKey);
    assertTrue(user.isPresent());
    Assertions.assertThat(user.get().getRoleKeysList()).isEmpty();
  }

  @Test
  public void shouldRejectIfRoleIsNotPresentEntityRemoval() {
    // given
    final var name = UUID.randomUUID().toString();
    final var roleRecord = ENGINE.role().newRole(name).create();

    // when
    final var notPresentRoleKey = 1L;
    final var notPresentUpdateRecord =
        ENGINE.role().addEntity(notPresentRoleKey).expectRejection().add();

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
    final var roleRecord = ENGINE.role().newRole(name).create();

    // when
    final var createdRole = roleRecord.getValue();
    final var roleKey = createdRole.getRoleKey();
    final var notPresentUpdateRecord =
        ENGINE
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
    final var roleKey = ENGINE.role().newRole(name).create().getValue().getRoleKey();
    ENGINE
        .authorization()
        .permission()
        .withOwnerKey(roleKey)
        .withOwnerType(AuthorizationOwnerType.ROLE)
        .withResourceType(AuthorizationResourceType.ROLE)
        .withAction(PermissionAction.REMOVE)
        .add();

    // when
    ENGINE.role().deleteRole(roleKey).withName(name).delete();

    final var deletedRecord = roleState.getRole(roleKey);
    assertTrue(deletedRecord.isEmpty());
  }

  @Test
  public void shouldRejectIfRoleIsNotPresentOnDeletion() {
    // when
    final var notPresentRoleKey = 1L;
    final var notPresentUpdateRecord =
        ENGINE.role().deleteRole(notPresentRoleKey).expectRejection().delete();

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to delete role with key '%s', but a role with this key doesn't exist."
                .formatted(notPresentRoleKey));
  }
}
