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
    final var groupName = "group";
    final var groupRecord = new GroupRecord().setGroupId(groupId).setName(groupName);
    groupState.create(groupRecord);

    final var updatedGroupName = "updatedGroup";
    groupRecord.setName(updatedGroupName);

    // when
    groupState.update(groupRecord);

    // then
    final var group = groupState.get(groupId);
    assertThat(group.isPresent()).isTrue();
    final var persistedGroup = group.get();
    assertThat(persistedGroup.getName()).isEqualTo(updatedGroupName);
    assertThat(persistedGroup.getGroupId()).isEqualTo(groupId);
  }

  @Test
  void shouldAddEntity() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var groupName = "group";
    final var groupRecord = new GroupRecord().setGroupId(groupId).setName(groupName);
    groupState.create(groupRecord);

    // when
    final var username = Strings.newRandomValidIdentityId();
    final var userEntityType = EntityType.USER;
    groupRecord.setEntityId(username).setEntityType(userEntityType);
    groupState.addEntity(groupRecord);

    // then
    final var entityType = groupState.getEntityType(groupId, username);
    assertThat(entityType.isPresent()).isTrue();
    assertThat(entityType.get()).isEqualTo(userEntityType);
  }

  @Test
  void shouldReturnEntitiesByType() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var groupName = "group";
    final var groupRecord = new GroupRecord().setGroupId(groupId).setName(groupName);
    groupState.create(groupRecord);
    final var username = Strings.newRandomValidIdentityId();
    groupRecord.setEntityId(username).setEntityType(EntityType.USER);
    groupState.addEntity(groupRecord);
    final var mappingId = Strings.newRandomValidIdentityId();
    groupRecord.setEntityId(mappingId).setEntityType(EntityType.MAPPING);
    groupState.addEntity(groupRecord);

    // when
    final var entities = groupState.getEntitiesByType(groupId);

    // then
    assertThat(entities)
        .containsEntry(EntityType.USER, List.of(username))
        .containsEntry(EntityType.MAPPING, List.of(mappingId));
  }

  @Test
  void shouldRemoveEntity() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var groupName = "group";
    final var groupRecord = new GroupRecord().setGroupId(groupId).setName(groupName);
    groupState.create(groupRecord);
    final var username = Strings.newRandomValidIdentityId();
    groupRecord.setEntityId(username).setEntityType(EntityType.USER);
    groupState.addEntity(groupRecord);
    final var mappingId = Strings.newRandomValidIdentityId();
    groupRecord.setEntityId(mappingId).setEntityType(EntityType.MAPPING);
    groupState.addEntity(groupRecord);

    // when
    groupState.removeEntity(groupId, username);

    // then
    final var entityType = groupState.getEntitiesByType(groupId);
    assertThat(entityType).containsOnly(Map.entry(EntityType.MAPPING, List.of(mappingId)));
  }

  @Test
  void shouldDeleteGroup() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var groupName = "group";
    final var groupRecord = new GroupRecord().setGroupId(groupId).setName(groupName);
    groupState.create(groupRecord);
    final var username = Strings.newRandomValidIdentityId();
    groupRecord.setEntityId(username).setEntityType(EntityType.USER);
    groupState.addEntity(groupRecord);
    final var mappingId = Strings.newRandomValidIdentityId();
    groupRecord.setEntityId(mappingId).setEntityType(EntityType.MAPPING);
    groupState.addEntity(groupRecord);

    // when
    groupState.delete(groupId);

    // then
    final var group = groupState.get(groupId);
    assertThat(group).isEmpty();

    final var entitiesByGroup = groupState.getEntitiesByType(groupId);
    assertThat(entitiesByGroup).isEmpty();
  }
}
