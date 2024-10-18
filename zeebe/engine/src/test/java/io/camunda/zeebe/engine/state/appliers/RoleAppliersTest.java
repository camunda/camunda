/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableRoleState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class RoleAppliersTest {

  private MutableProcessingState processingState;

  private MutableRoleState roleState;
  private MutableUserState userState;
  private MutableAuthorizationState authorizationState;
  private RoleDeletedApplier roleDeletedApplier;
  private RoleEntityAddedApplier roleEntityAddedApplier;

  @BeforeEach
  public void setup() {
    roleState = processingState.getRoleState();
    userState = processingState.getUserState();
    authorizationState = processingState.getAuthorizationState();
    roleDeletedApplier =
        new RoleDeletedApplier(
            processingState.getRoleState(),
            processingState.getUserState(),
            processingState.getAuthorizationState());
    roleEntityAddedApplier =
        new RoleEntityAddedApplier(processingState.getRoleState(), processingState.getUserState());
  }

  @Test
  void shouldAddEntityToRole() {
    // given
    final long entityKey = 1L;
    userState.create(
        new UserRecord()
            .setUserKey(entityKey)
            .setUsername("username")
            .setName("Foo")
            .setEmail("foo@bar.com")
            .setPassword("password"));
    final long roleKey = 11L;
    final var roleRecord = new RoleRecord().setRoleKey(roleKey).setName("foo");
    roleState.create(roleRecord);
    roleRecord.setEntityKey(entityKey).setEntityType(EntityType.USER);

    // when
    roleEntityAddedApplier.applyState(roleKey, roleRecord);

    // then
    assertThat(roleState.getEntitiesByType(roleKey).get(EntityType.USER))
        .containsExactly(entityKey);
    final var persistedUser = userState.getUser(entityKey).get();
    assertThat(persistedUser.getRoleKeysList()).containsExactly(roleKey);
  }

  @Test
  void shouldDeleteRole() {
    // given
    userState.create(
        new UserRecord()
            .setUserKey(1L)
            .setUsername("username")
            .setName("Foo")
            .setEmail("foo@bar.com")
            .setPassword("password"));
    final long roleKey = 11L;
    final var roleRecord = new RoleRecord().setRoleKey(roleKey).setName("foo");
    roleState.create(roleRecord);
    roleRecord.setEntityKey(1L).setEntityType(EntityType.USER);
    roleEntityAddedApplier.applyState(roleKey, roleRecord);
    authorizationState.insertOwnerTypeByKey(roleKey, AuthorizationOwnerType.ROLE);
    authorizationState.createOrAddPermission(
        roleKey, AuthorizationResourceType.ROLE, PermissionType.DELETE, List.of("role1", "role2"));

    // when
    roleDeletedApplier.applyState(roleKey, roleRecord);

    // then
    assertThat(roleState.getRole(roleKey)).isEmpty();
    final var persistedUser = userState.getUser(1L).get();
    assertThat(persistedUser.getRoleKeysList()).isEmpty();
    final var ownerType = authorizationState.getOwnerType(roleKey);
    assertThat(ownerType).isEmpty();
    final var resourceIdentifiers =
        authorizationState.getResourceIdentifiers(
            roleKey, AuthorizationResourceType.ROLE, PermissionType.DELETE);
    assertThat(resourceIdentifiers).isEmpty();
  }
}
