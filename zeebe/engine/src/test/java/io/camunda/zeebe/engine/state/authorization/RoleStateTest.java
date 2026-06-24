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
    final var roleId = Strings.newRandomValidIdentityId();
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
    final var role = roleState.getRole(Strings.newRandomValidIdentityId());

    // then
    assertThat(role).isEmpty();
  }

  @Test
  void shouldUpdateRole() {
    // given
    final var roleKey = 123L;
    final var roleId = Strings.newRandomValidIdentityId();
    final var roleRecord =
        new RoleRecord().setRoleKey(roleKey).setRoleId(roleId).setName("foo").setDescription("bar");
    roleState.create(roleRecord);

    // when
    final String updatedName = "updatedName";
    final var updatedRecord =
        new RoleRecord().setRoleKey(roleKey).setRoleId(roleId).setName(updatedName);
    roleState.update(updatedRecord);

    // then
    final var persistedRole = roleState.getRole(roleId).get();
    assertThat(persistedRole.getRoleKey()).isEqualTo(roleKey);
    assertThat(persistedRole.getName()).isEqualTo(updatedName);
  }

  @Test
  void shouldDeleteRole() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var roleName = "foo";
    final var roleRecord = new RoleRecord().setRoleId(roleId).setName(roleName);
    roleState.create(roleRecord);

    // when
    roleState.delete(roleRecord);

    // then
    final var deletedRole = roleState.getRole(roleId);
    assertThat(deletedRole).isEmpty();
  }

  @Test
  void shouldReturnNewCopiesOnGet() {
    // given
    final String id = "id";
    roleState.create(new RoleRecord().setRoleId(id).setRoleKey(123L).setName("name"));

    // when
    final var role1 = roleState.getRole(id).get();
    final var role2 = roleState.getRole(id).get();

    // then
    assertThat(role1).isNotSameAs(role2);
  }
}
