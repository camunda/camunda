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

    final var roleKeyByName = roleState.getRoleKeyByName(roleName);
    assertThat(roleKeyByName).isEqualTo(roleKey);
  }

  @Test
  void shouldReturnNullIfRoleDoesNotExist() {
    // when
    final var role = roleState.getRole(1L);

    // then
    assertThat(role).isEmpty();
  }

  @Test
  void shouldReturnNegativeOneIfRoleKeyByNameDoesNotExist() {
    // when
    final var roleKey = roleState.getRoleKeyByName("foo");

    // then
    assertThat(roleKey).isEqualTo(-1L);
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

    final var roleKeyByName = roleState.getRoleKeyByName(updatedName);
    assertThat(roleKeyByName).isEqualTo(roleKey);
  }

  @Test
  void shouldAddEntity() {
    // given
    final long roleKey = 1L;
    final String roleName = "foo";
    final var roleRecord = new RoleRecord().setRoleKey(roleKey).setName(roleName);
    roleState.createRole(roleRecord);

    // when
    roleRecord.setEntityKey(1L).setEntityType(EntityType.USER);
    roleState.addEntity(roleRecord);

    // then
    final var persistedRole = roleState.getRole(roleKey).get();
    assertThat(persistedRole.getRoleKey()).isEqualTo(roleKey);
    assertThat(persistedRole.getName()).isEqualTo(roleName);
    assertThat(persistedRole.getEntityKey()).isEqualTo(1L);
    assertThat(persistedRole.getEntityType()).isEqualTo(EntityType.USER);

    final var entityType = roleState.getEntityType(roleKey, 1L);
    assertThat(entityType).isEqualTo(EntityType.USER);
  }
}
