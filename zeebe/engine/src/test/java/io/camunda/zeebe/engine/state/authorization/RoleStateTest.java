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
    final long roleKey = 1L;
    final String roleName = "foo";
    final var roleRecord = new RoleRecord().setRoleKey(roleKey).setName(roleName);

    // when
    roleState.create(roleRecord);

    // then
    final var persistedRole = roleState.getRole(roleKey).get();
    assertThat(persistedRole.getRoleKey()).isEqualTo(roleKey);
    assertThat(persistedRole.getName()).isEqualTo(roleName);
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
    final long roleKey = 1L;
    final var roleRecord = new RoleRecord().setRoleKey(roleKey).setName("foo");
    roleState.create(roleRecord);

    // when
    final String updatedName = "updatedName";
    final var updatedRecord = new RoleRecord().setRoleKey(roleKey).setName(updatedName);
    roleState.update(updatedRecord);

    // then
    final var persistedRole = roleState.getRole(roleKey).get();
    assertThat(persistedRole.getRoleKey()).isEqualTo(roleKey);
    assertThat(persistedRole.getName()).isEqualTo(updatedName);
  }

  @Test
  void shouldAddEntity() {
    // given
    final long roleKey = 1L;
    final String roleName = "foo";
    final var roleRecord = new RoleRecord().setRoleKey(roleKey).setName(roleName);
    roleState.create(roleRecord);

    // when
    roleRecord.setEntityKey(1L).setEntityType(EntityType.USER);
    roleState.addEntity(roleRecord);

    // then
    final var entityType = roleState.getEntityType(roleKey, 1L).get();
    assertThat(entityType).isEqualTo(EntityType.USER);
  }

  @Test
  void shouldRemoveEntity() {
    // given
    final long roleKey = 1L;
    final String roleName = "foo";
    final var roleRecord = new RoleRecord().setRoleKey(roleKey).setName(roleName);
    roleState.create(roleRecord);
    roleRecord.setEntityKey(1L).setEntityType(EntityType.USER);
    roleState.addEntity(roleRecord);
    roleState.addEntity(
        new RoleRecord().setRoleKey(roleKey).setEntityKey(2L).setEntityType(EntityType.USER));

    // when
    roleState.removeEntity(roleKey, 1L);

    // then
    final var deletedEntity = roleState.getEntityType(roleKey, 1L);
    assertThat(deletedEntity).isEmpty();
    final var entityType = roleState.getEntityType(roleKey, 2L).get();
    assertThat(entityType).isEqualTo(EntityType.USER);
  }

  @Test
  void shouldDeleteRole() {
    // given
    final long roleKey = 1L;
    final String roleName = "foo";
    final var roleRecord = new RoleRecord().setRoleKey(roleKey).setName(roleName);
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
    final long roleKey = 1L;
    final String roleName = "foo";
    final var roleRecord = new RoleRecord().setRoleKey(roleKey).setName(roleName);
    roleState.create(roleRecord);
    roleRecord.setEntityKey(1L).setEntityType(EntityType.USER);
    roleState.addEntity(roleRecord);
    roleRecord.setEntityKey(2L).setEntityType(EntityType.UNSPECIFIED);
    roleState.addEntity(roleRecord);

    // when
    final var entities = roleState.getEntitiesByType(roleKey);

    // then
    assertThat(entities.get(EntityType.USER)).containsExactly(1L);
    assertThat(entities.get(EntityType.UNSPECIFIED)).containsExactly(2L);
  }
}
