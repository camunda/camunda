/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.group;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableGroupState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class GroupStateTest {

  private MutableProcessingState processingState;
  private MutableGroupState groupState;

  @BeforeEach
  public void setup() {
    groupState = processingState.getGroupState();
  }

  @Test
  void shouldCreateGroup() {
    // given
    final var groupId = "1";
    final var groupName = "group";
    final var groupRecord = new GroupRecord().setGroupId(groupId).setName(groupName);

    // when
    groupState.create(groupRecord);

    // then
    final var group = groupState.get(groupId);
    assertThat(group.isPresent()).isTrue();
    final var persistedGroup = group.get();
    assertThat(persistedGroup.getName()).isEqualTo(groupName);
  }

  @Test
  void shouldReturnNullIfGroupDoesNotExist() {
    // given
    final var groupId = "groupId";

    // when
    final var group = groupState.get(groupId);

    // then
    assertThat(group.isPresent()).isFalse();
  }

  @Test
  void shouldUpdateGroup() {
    // given
    final var groupId = "1";
    final var groupKey = Long.parseLong(groupId);
    final var groupName = "group";
    final var groupRecord =
        new GroupRecord().setGroupKey(groupKey).setGroupId(groupId).setName(groupName);
    groupState.create(groupRecord);

    final var updatedGroupName = "updatedGroup";
    groupRecord.setName(updatedGroupName);

    // when
    groupState.update(groupKey, groupRecord);

    // then
    final var group = groupState.get(groupId);
    assertThat(group.isPresent()).isTrue();
    final var persistedGroup = group.get();
    assertThat(persistedGroup.getGroupKey()).isEqualTo(groupKey);
    assertThat(persistedGroup.getName()).isEqualTo(updatedGroupName);
  }

  @Test
  void shouldAddEntity() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var groupName = "group";
    final var groupRecord = new GroupRecord().setGroupId(groupId).setName(groupName);
    groupState.create(groupRecord);

    // when
    final var userKey = 2L;
    final var userEntityType = EntityType.USER;
    groupRecord.setEntityKey(userKey).setEntityType(userEntityType);
    groupState.addEntity(groupRecord);

    // then
    final var entityType = groupState.getEntityType(groupId, userKey);
    assertThat(entityType.isPresent()).isTrue();
    assertThat(entityType.get()).isEqualTo(userEntityType);
  }

  @Test
  void shouldReturnEntitiesByType() {
    // given
    final var groupId = "1";
    final var groupKey = Long.parseLong(groupId);
    final var groupName = "group";
    final var groupRecord =
        new GroupRecord().setGroupKey(groupKey).setGroupId(groupId).setName(groupName);
    groupState.create(groupRecord);
    final var userKey = 2L;
    groupRecord.setEntityKey(2L).setEntityType(EntityType.USER);
    groupState.addEntity(groupRecord);
    final var mappingKey = 3L;
    groupRecord.setEntityKey(mappingKey).setEntityType(EntityType.MAPPING);
    groupState.addEntity(groupRecord);

    // when
    final var entities = groupState.getEntitiesByType(groupKey);

    // then
    assertThat(entities)
        .containsEntry(EntityType.USER, List.of(userKey))
        .containsEntry(EntityType.MAPPING, List.of(mappingKey));
  }

  @Test
  void shouldRemoveEntity() {
    // given
    final var groupId = "1";
    final var groupKey = Long.parseLong(groupId);
    final var groupName = "group";
    final var groupRecord =
        new GroupRecord().setGroupKey(groupKey).setGroupId(groupId).setName(groupName);
    groupState.create(groupRecord);
    final var userKey = 2L;
    groupRecord.setEntityKey(userKey).setEntityType(EntityType.USER);
    groupState.addEntity(groupRecord);
    final var mappingKey = 3L;
    groupRecord.setEntityKey(mappingKey).setEntityType(EntityType.MAPPING);
    groupState.addEntity(groupRecord);

    // when
    groupState.removeEntity(groupKey, userKey);

    // then
    final var entityType = groupState.getEntitiesByType(groupKey);
    assertThat(entityType).containsOnly(Map.entry(EntityType.MAPPING, List.of(mappingKey)));
  }

  @Test
  void shouldDeleteGroup() {
    // given
    final var groupId = "1";
    final var groupKey = Long.parseLong(groupId);
    final var groupName = "group";
    final var groupRecord =
        new GroupRecord().setGroupKey(groupKey).setGroupId(groupId).setName(groupName);
    groupState.create(groupRecord);
    groupRecord.setEntityKey(2L).setEntityType(EntityType.USER);
    groupState.addEntity(groupRecord);
    groupRecord.setEntityKey(3L).setEntityType(EntityType.MAPPING);
    groupState.addEntity(groupRecord);

    // when
    groupState.delete(groupId);

    // then
    final var group = groupState.get(groupId);
    assertThat(group).isEmpty();

    final var entitiesByGroup = groupState.getEntitiesByType(groupKey);
    assertThat(entitiesByGroup).isEmpty();
  }

  @Test
  void shouldAddTenant() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var groupName = "group";
    final var tenantId = "tenant1";
    final var groupRecord = new GroupRecord().setGroupId(groupId).setName(groupName);
    groupState.create(groupRecord);

    // when
    groupState.addTenant(groupId, tenantId);

    // then
    final var group = groupState.get(groupId);
    assertThat(group.isPresent()).isTrue();
    assertThat(group.get().getTenantIdsList()).containsExactly(tenantId);
  }

  @Test
  void shouldRemoveTenant() {
    // given
    final var groupId = "1";
    final var groupKey = Long.parseLong(groupId);
    final var groupName = "group";
    final var tenantId1 = "tenant1";
    final var tenantId2 = "tenant2";

    // Create a group and add tenants
    final var groupRecord =
        new GroupRecord().setGroupKey(groupKey).setGroupId(groupId).setName(groupName);
    groupState.create(groupRecord);
    groupState.addTenant(groupId, tenantId1);
    groupState.addTenant(groupId, tenantId2);

    // Ensure tenants are added correctly
    final var groupBeforeRemove = groupState.get(groupId);
    assertThat(groupBeforeRemove.isPresent()).isTrue();
    assertThat(groupBeforeRemove.get().getTenantIdsList()).containsExactly(tenantId1, tenantId2);

    // when
    groupState.removeTenant(groupKey, tenantId1);

    // then
    final var groupAfterRemove = groupState.get(groupId);
    assertThat(groupAfterRemove.isPresent()).isTrue();
    assertThat(groupAfterRemove.get().getTenantIdsList()).containsExactly(tenantId2);
  }
}
