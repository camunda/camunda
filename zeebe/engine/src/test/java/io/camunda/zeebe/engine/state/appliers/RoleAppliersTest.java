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
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
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
    assertThat(
            membershipState.getRelations(
                EntityType.USER, Long.toString(entityKey), RelationType.ROLE))
        .singleElement()
        .isEqualTo(Long.toString(roleKey));
  }

  @Test
  void shouldAddEntityToRoleWithTypeMapping() {
    // given
    final long entityKey = 1L;
    mappingState.create(
        new MappingRecord()
            .setMappingKey(entityKey)
            .setClaimName("claimName")
            .setClaimValue("claimValue"));
    final long roleKey = 11L;
    final var roleRecord = new RoleRecord().setRoleKey(roleKey).setName("foo");
    roleState.create(roleRecord);
    roleRecord.setEntityKey(entityKey).setEntityType(EntityType.MAPPING);

    // when
    roleEntityAddedApplier.applyState(roleKey, roleRecord);

    // then
    assertThat(roleState.getEntitiesByType(roleKey).get(EntityType.MAPPING))
        .containsExactly(entityKey);
    final var persistedMapping = mappingState.get(entityKey).get();
    assertThat(persistedMapping.getRoleKeysList()).containsExactly(roleKey);
  }

  @Test
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
            membershipState.getRelations(
                EntityType.USER, Long.toString(userKey), RelationType.ROLE))
        .isEmpty();
  }

  @Test
  void shouldRemoveEntityFromRoleWithTypeMapping() {
    // given
    final long entityKey = 1L;
    mappingState.create(
        new MappingRecord()
            .setMappingKey(entityKey)
            .setClaimName("claimName")
            .setClaimValue("claimValue"));
    final long roleKey = 11L;
    mappingState.addRole(entityKey, 11L);
    final var roleRecord = new RoleRecord().setRoleKey(roleKey).setName("foo");
    roleState.create(roleRecord);
    roleRecord.setEntityKey(entityKey).setEntityType(EntityType.MAPPING);
    roleState.addEntity(roleRecord);

    // when
    roleEntityRemovedApplier.applyState(roleKey, roleRecord);

    // then
    assertThat(roleState.getEntitiesByType(roleKey)).isEmpty();
    final var persistedMapping = mappingState.get(entityKey).get();
    assertThat(persistedMapping.getRoleKeysList()).isEmpty();
  }
}
