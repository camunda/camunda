/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableRoleState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.Strings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class RoleStateTest {

  private MutableProcessingState processingState;
  private MutableRoleState roleState;

  @BeforeEach
  public void setup() {
    roleState = processingState.getRoleState();
  }

  @Test
  void shouldCreateRole() {
    // given
    final var roleKey = 1L;
    final var roleId = "1";
    final var roleName = "foo";
    final var roleDescription = "bar";
    final var roleRecord =
        new RoleRecord()
            .setRoleKey(roleKey)
            .setRoleId(roleId)
            .setName(roleName)
            .setDescription(roleDescription);

    // when
    roleState.create(roleRecord);

    // then
    final var persistedRole = roleState.getRole(roleId).get();
    assertThat(persistedRole.getRoleKey()).isEqualTo(roleKey);
    assertThat(persistedRole.getRoleId()).isEqualTo(roleId);
    assertThat(persistedRole.getName()).isEqualTo(roleName);
    assertThat(persistedRole.getDescription()).isEqualTo(roleDescription);
  }

  @Test
  void shouldReturnNullIfRoleDoesNotExist() {
    // when
    final var role = roleState.getRole(1L);

    // then
    assertThat(role).isEmpty();
  }

  @Test
  void shouldUpdateRole() {
    // given
    final var roleKey = 123L;
    final var roleId = "123";
    final var roleRecord =
        new RoleRecord().setRoleKey(roleKey).setRoleId(roleId).setName("foo").setDescription("bar");
    roleState.create(roleRecord);

    // when
    final String updatedName = "updatedName";
    final var updatedRecord =
        new RoleRecord().setRoleKey(roleKey).setRoleId(roleId).setName(updatedName);
    roleState.update(updatedRecord);

    // then
    final var persistedRole = roleState.getRole(roleKey).get();
    assertThat(persistedRole.getRoleKey()).isEqualTo(roleKey);
    assertThat(persistedRole.getName()).isEqualTo(updatedName);
  }

  @Test
  void shouldAddEntity() {
    // given
    final var roleKey = 123L;
    final var roleId = Strings.newRandomValidIdentityId();
    final var roleName = "foo";
    final var roleRecord = new RoleRecord().setRoleKey(roleKey).setRoleId(roleId).setName(roleName);
    roleState.create(roleRecord);

    // when
    final var entityId = Strings.newRandomValidUsername();
    roleRecord.setEntityId(entityId).setEntityType(EntityType.USER);
    roleState.addEntity(roleRecord);

    // then
    final var entityType = roleState.getEntityType(roleId, entityId).get();
    assertThat(entityType).isEqualTo(EntityType.USER);
  }

  @Test
  void shouldRemoveEntity() {
    // given
    // TODO use a proper role id https://github.com/camunda/camunda/issues/30117
    final var roleKey = 123L;
    final var roleId = "123";
    final var roleName = "foo";
    final var roleRecord = new RoleRecord().setRoleKey(roleKey).setRoleId(roleId).setName(roleName);
    roleState.create(roleRecord);
    final var entityId1 = "456";
    roleRecord.setEntityId(entityId1).setEntityType(EntityType.USER);
    roleState.addEntity(roleRecord);
    final var entityId2 = "789";
    roleState.addEntity(
        new RoleRecord().setRoleId(roleId).setEntityId(entityId2).setEntityType(EntityType.USER));

    // when
    roleState.removeEntity(roleKey, 456L);

    // then
    final var deletedEntity = roleState.getEntityType(roleKey, 456L);
    assertThat(deletedEntity).isEmpty();
    final var entityType = roleState.getEntityType(roleKey, 789L).get();
    assertThat(entityType).isEqualTo(EntityType.USER);
  }

  @Test
  void shouldDeleteRole() {
    // given
    // TODO use a proper role id https://github.com/camunda/camunda/issues/30114
    final var roleKey = 123L;
    final var roleId = "123";
    final var roleName = "foo";
    final var roleRecord = new RoleRecord().setRoleKey(roleKey).setRoleId(roleId).setName(roleName);
    roleState.create(roleRecord);
    roleRecord.setEntityKey(1L).setEntityType(EntityType.USER);
    roleState.addEntity(roleRecord);

    // when
    roleState.delete(roleRecord);

    // then
    final var deletedRole = roleState.getRole(roleKey);
    assertThat(deletedRole).isEmpty();
    final var deletedEntity = roleState.getEntityType(roleKey, 1L);
    assertThat(deletedEntity).isEmpty();
  }

  @Test
  void shouldReturnEntityByType() {
    // given
    final var roleKey = 123L;
    final var roleId = Strings.newRandomValidIdentityId();
    final var roleName = "foo";
    final var roleRecord = new RoleRecord().setRoleId(roleId).setRoleKey(roleKey).setName(roleName);
    roleState.create(roleRecord);
    final var entityId1 = Strings.newRandomValidUsername();
    roleRecord.setEntityId(entityId1).setEntityType(EntityType.USER);
    roleState.addEntity(roleRecord);
    final var entityId2 = Strings.newRandomValidUsername();
    roleRecord.setEntityId(entityId2).setEntityType(EntityType.UNSPECIFIED);
    roleState.addEntity(roleRecord);

    // when
    final var entities = roleState.getEntitiesByType(roleId);

    // then
    assertThat(entities.get(EntityType.USER)).containsExactly(entityId1);
    assertThat(entities.get(EntityType.UNSPECIFIED)).containsExactly(entityId2);
  }
}
