/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutableMembershipState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableRoleState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.Strings;
import java.util.Set;
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
  private RoleEntityRemovedApplier roleEntityRemovedApplier;
  private MutableMembershipState membershipState;

  @BeforeEach
  public void setup() {
    roleState = processingState.getRoleState();
    userState = processingState.getUserState();
    authorizationState = processingState.getAuthorizationState();
    membershipState = processingState.getMembershipState();
    roleDeletedApplier = new RoleDeletedApplier(processingState.getRoleState());
    roleEntityAddedApplier = new RoleEntityAddedApplier(processingState);
    roleEntityRemovedApplier = new RoleEntityRemovedApplier(processingState);
  }

  @Test
  void shouldAddEntityToRoleWithTypeUser() {
    // given
    final long entityKey = 1L;
    final var username = "username";
    userState.create(
        new UserRecord()
            .setUserKey(entityKey)
            .setUsername(username)
            .setName("Foo")
            .setEmail("foo@bar.com")
            .setPassword("password"));
    final long roleKey = 11L;
    final var roleId = Strings.newRandomValidIdentityId();
    final var roleRecord = new RoleRecord().setRoleKey(roleKey).setRoleId(roleId).setName("foo");
    roleState.create(roleRecord);
    roleRecord.setEntityId(username).setEntityType(EntityType.USER);

    // when
    roleEntityAddedApplier.applyState(roleKey, roleRecord);

    // then
    assertThat(membershipState.getMemberships(EntityType.USER, username, RelationType.ROLE))
        .singleElement()
        .isEqualTo(roleId);
  }

  @Test
  void shouldDeleteRole() {
    // given
    final String roleId = Strings.newRandomValidIdentityId();
    final String roleName = "foo";
    final var roleRecord = new RoleRecord().setRoleId(roleId).setName(roleName);
    roleState.create(roleRecord);
    authorizationState.create(
        1L,
        new AuthorizationRecord()
            .setAuthorizationKey(1L)
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId("role1")
            .setResourceType(AuthorizationResourceType.ROLE)
            .setPermissionTypes(Set.of(PermissionType.DELETE))
            .setOwnerType(AuthorizationOwnerType.ROLE)
            .setOwnerId(roleId));
    authorizationState.create(
        2L,
        new AuthorizationRecord()
            .setAuthorizationKey(2L)
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId("role2")
            .setResourceType(AuthorizationResourceType.ROLE)
            .setPermissionTypes(Set.of(PermissionType.DELETE))
            .setOwnerType(AuthorizationOwnerType.ROLE)
            .setOwnerId(roleId));

    // when
    roleDeletedApplier.applyState(1L, roleRecord);

    // then
    assertThat(roleState.getRole(roleId)).isEmpty();
  }

  @Test
  void shouldRemoveEntityFromRoleWithTypeUser() {
    // given
    final var username = "foo";
    final var userKey = 123L;
    userState.create(
        new UserRecord()
            .setUserKey(userKey)
            .setUsername(username)
            .setName("Foo")
            .setEmail("foo@bar.com")
            .setPassword("password"));
    final long roleKey = 11L;
    final var roleId = Strings.newRandomValidIdentityId();
    final var roleRecord = new RoleRecord().setRoleId(roleId).setRoleKey(roleKey).setName("foo");
    roleState.create(roleRecord);
    roleRecord.setRoleId(roleId).setEntityId(username).setEntityType(EntityType.USER);
    roleEntityAddedApplier.applyState(roleKey, roleRecord);

    // when
    roleEntityRemovedApplier.applyState(roleKey, roleRecord);

    // then
    assertThat(membershipState.getMemberships(EntityType.USER, username, RelationType.ROLE))
        .isEmpty();
  }
}
