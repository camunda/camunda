/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableGroupState;
import io.camunda.zeebe.engine.state.mutable.MutableMappingState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class GroupAppliersTest {

  private MutableProcessingState processingState;

  private MutableGroupState groupState;
  private MutableUserState userState;
  private MutableMappingState mappingState;

  private GroupCreatedApplier groupCreatedApplier;
  private GroupUpdatedApplier groupUpdatedApplier;
  private GroupEntityAddedApplier groupEntityAddedApplier;
  private GroupEntityRemovedApplier groupEntityRemovedApplier;
  private GroupDeletedApplier groupDeletedApplier;

  @BeforeEach
  public void setup() {
    groupState = processingState.getGroupState();
    userState = processingState.getUserState();
    mappingState = processingState.getMappingState();

    groupCreatedApplier = new GroupCreatedApplier(groupState);
    groupUpdatedApplier = new GroupUpdatedApplier(groupState);
    groupEntityAddedApplier = new GroupEntityAddedApplier(processingState);
    groupEntityRemovedApplier = new GroupEntityRemovedApplier(processingState);
    groupDeletedApplier = new GroupDeletedApplier(processingState);
  }

  @Test
  void shouldCreateGroup() {
    // given
    final var groupKey = 1L;
    final var groupName = "group";
    final var groupRecord = new GroupRecord().setGroupKey(groupKey).setName(groupName);

    // when
    groupCreatedApplier.applyState(groupKey, groupRecord);

    // then
    final var group = groupState.get(groupKey);
    assertThat(group.isPresent()).isTrue();
    final var persistedGroup = group.get();
    assertThat(persistedGroup.getGroupKey()).isEqualTo(groupKey);
    assertThat(persistedGroup.getName()).isEqualTo(groupName);
  }

  @Test
  void shouldUpdateGroup() {
    // given
    final var groupKey = 1L;
    final var groupName = "group";
    final var updatedGroupName = "updatedGroup";
    final var groupRecord = new GroupRecord().setGroupKey(groupKey).setName(groupName);
    groupState.create(groupKey, groupRecord);
    final var persistedGroup = groupState.get(groupKey);
    assertThat(persistedGroup.isPresent()).isTrue();
    assertThat(persistedGroup.get().getName()).isEqualTo(groupName);
    final var updatedGroupRecord =
        new GroupRecord().setGroupKey(groupKey).setName(updatedGroupName);

    // when
    groupUpdatedApplier.applyState(groupKey, updatedGroupRecord);

    // then
    final var group = groupState.get(groupKey);
    assertThat(group.isPresent()).isTrue();
    final var persistedUpdatedGroup = group.get();
    assertThat(persistedUpdatedGroup.getGroupKey()).isEqualTo(groupKey);
    assertThat(persistedUpdatedGroup.getName()).isEqualTo(updatedGroupRecord.getName());
  }

  @Test
  void shouldAddUserEntityToGroup() {
    // given
    final var entityKey = 1L;
    final var userRecord =
        new UserRecord()
            .setUserKey(entityKey)
            .setName("test")
            .setUsername("username")
            .setPassword("password")
            .setEmail("test@example.com");
    userState.create(userRecord);
    final var groupKey = 2L;
    final var groupName = "group";
    final var entityType = EntityType.USER;
    final var groupRecord = new GroupRecord().setGroupKey(groupKey).setName(groupName);
    groupState.create(groupKey, groupRecord);
    groupRecord.setEntityKey(entityKey).setEntityType(entityType);

    // when
    groupEntityAddedApplier.applyState(groupKey, groupRecord);

    // then
    final var entitiesByType = groupState.getEntitiesByType(groupKey);
    assertThat(entitiesByType).containsOnly(Map.entry(entityType, List.of(entityKey)));
    final var persistedUser = userState.getUser(entityKey).get();
    assertThat(persistedUser.getGroupKeysList()).containsExactly(groupKey);
  }

  @Test
  void shouldAddMappingEntityToGroup() {
    // given
    final var entityKey = 1L;
    final var mappingRecord =
        new MappingRecord()
            .setMappingKey(entityKey)
            .setClaimName("claimName")
            .setClaimValue("claimValue");
    mappingState.create(mappingRecord);
    final var groupKey = 2L;
    final var groupName = "group";
    final var entityType = EntityType.MAPPING;
    final var groupRecord = new GroupRecord().setGroupKey(groupKey).setName(groupName);
    groupState.create(groupKey, groupRecord);
    groupRecord.setEntityKey(entityKey).setEntityType(entityType);

    // when
    groupEntityAddedApplier.applyState(groupKey, groupRecord);

    // then
    final var entitiesByType = groupState.getEntitiesByType(groupKey);
    assertThat(entitiesByType).containsOnly(Map.entry(entityType, List.of(entityKey)));
    final var persistedMapping = mappingState.get(entityKey).get();
    assertThat(persistedMapping.getGroupKeysList()).containsExactly(groupKey);
  }

  @Test
  void shoulRemoveUserEntityFromGroup() {
    // given
    final var entityKey = 1L;
    final var userRecord =
        new UserRecord()
            .setUserKey(entityKey)
            .setName("test")
            .setUsername("username")
            .setPassword("password")
            .setEmail("test@example.com");
    userState.create(userRecord);
    final var groupKey = 2L;
    final var groupName = "group";
    final var entityType = EntityType.USER;
    final var groupRecord = new GroupRecord().setGroupKey(groupKey).setName(groupName);
    groupState.create(groupKey, groupRecord);
    groupRecord.setEntityKey(entityKey).setEntityType(entityType);
    groupEntityAddedApplier.applyState(groupKey, groupRecord);

    // when
    groupEntityRemovedApplier.applyState(groupKey, groupRecord);

    // then
    final var entitiesByType = groupState.getEntitiesByType(groupKey);
    assertThat(entitiesByType).isEmpty();
    final var persistedUser = userState.getUser(entityKey).get();
    assertThat(persistedUser.getGroupKeysList()).isEmpty();
  }

  @Test
  void shouldRemoveMappingEntityFromGroup() {
    // given
    final var entityKey = 1L;
    final var mappingRecord =
        new MappingRecord()
            .setMappingKey(entityKey)
            .setClaimName("claimName")
            .setClaimValue("claimValue");
    mappingState.create(mappingRecord);
    final var groupKey = 2L;
    final var groupName = "group";
    final var entityType = EntityType.MAPPING;
    final var groupRecord = new GroupRecord().setGroupKey(groupKey).setName(groupName);
    groupState.create(groupKey, groupRecord);
    groupRecord.setEntityKey(entityKey).setEntityType(entityType);
    groupEntityAddedApplier.applyState(groupKey, groupRecord);

    // when
    groupEntityRemovedApplier.applyState(groupKey, groupRecord);

    // then
    final var entitiesByType = groupState.getEntitiesByType(groupKey);
    assertThat(entitiesByType).isEmpty();
    final var persistedMapping = mappingState.get(entityKey).get();
    assertThat(persistedMapping.getGroupKeysList()).isEmpty();
  }

  @Test
  void shouldDeleteGroup() {
    // given
    // a group
    final var groupKey = 1L;
    final var groupName = "group";
    final var groupRecord = new GroupRecord().setGroupKey(groupKey).setName(groupName);
    groupCreatedApplier.applyState(groupKey, groupRecord);

    // when
    groupDeletedApplier.applyState(groupKey, groupRecord);

    // then
    // the group state is cleaned up
    final var group = groupState.get(groupKey);
    assertThat(group.isPresent()).isFalse();
    final var entitiesByGroup = groupState.getEntitiesByType(groupKey);
    assertThat(entitiesByGroup).isEmpty();
  }
}
