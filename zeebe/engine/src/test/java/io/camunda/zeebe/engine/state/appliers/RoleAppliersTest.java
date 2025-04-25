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
import io.camunda.zeebe.engine.state.mutable.MutableMappingState;
import io.camunda.zeebe.engine.state.mutable.MutableMembershipState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableRoleState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.Strings;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class RoleAppliersTest {

  private MutableProcessingState processingState;

  private MutableRoleState roleState;
  private MutableUserState userState;
  private MutableAuthorizationState authorizationState;
  private MutableMappingState mappingState;
  private RoleDeletedApplier roleDeletedApplier;
  private RoleEntityAddedApplier roleEntityAddedApplier;
  private RoleEntityRemovedApplier roleEntityRemovedApplier;
  private MutableMembershipState membershipState;

  @BeforeEach
  public void setup() {
    roleState = processingState.getRoleState();
    userState = processingState.getUserState();
    authorizationState = processingState.getAuthorizationState();
    mappingState = processingState.getMappingState();
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
  @Disabled("https://github.com/camunda/camunda/issues/30114")
  void shouldDeleteRole() {
    // given
    final long roleKey = 11L;
    final String roleId = String.valueOf(roleKey);
    final String roleName = "foo";
    final var roleRecord = new RoleRecord().setRoleKey(roleKey).setName(roleName);
    roleState.create(roleRecord);
    authorizationState.create(
        1L,
        new AuthorizationRecord()
            .setAuthorizationKey(1L)
            .setResourceId("role1")
            .setResourceType(AuthorizationResourceType.ROLE)
            .setPermissionTypes(Set.of(PermissionType.DELETE))
            .setOwnerType(AuthorizationOwnerType.ROLE)
            .setOwnerId(roleId));
    authorizationState.create(
        2L,
        new AuthorizationRecord()
            .setAuthorizationKey(2L)
            .setResourceId("role2")
            .setResourceType(AuthorizationResourceType.ROLE)
            .setPermissionTypes(Set.of(PermissionType.DELETE))
            .setOwnerType(AuthorizationOwnerType.ROLE)
            .setOwnerId(roleId));

    // when
    roleDeletedApplier.applyState(roleKey, roleRecord);

    // then
    assertThat(roleState.getRole(roleKey)).isEmpty();
  }

  @Test
  @Disabled("https://github.com/camunda/camunda/issues/30117")
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
    final var roleRecord = new RoleRecord().setRoleKey(roleKey).setName("foo");
    roleState.create(roleRecord);
    roleRecord.setEntityKey(userKey).setEntityType(EntityType.USER);
    roleEntityAddedApplier.applyState(roleKey, roleRecord);

    // when
    roleEntityRemovedApplier.applyState(roleKey, roleRecord);

    // then
    assertThat(roleState.getEntitiesByType(roleKey)).isEmpty();
    assertThat(
            membershipState.getMemberships(
                EntityType.USER, Long.toString(userKey), RelationType.ROLE))
        .isEmpty();
  }
}
