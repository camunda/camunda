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
import io.camunda.zeebe.test.util.Strings;
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
    final var description = "description";
    final var groupRecord =
        new GroupRecord().setGroupId(groupId).setName(groupName).setDescription(description);

    // when
    groupState.create(groupRecord);

    // then
    final var group = groupState.get(groupId);
    assertThat(group.isPresent()).isTrue();
    final var persistedGroup = group.get();
    assertThat(persistedGroup.getName()).isEqualTo(groupName);
    assertThat(persistedGroup.getDescription()).isEqualTo(description);
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
  void shouldUpdateGroupDescription() {
    // given
    final var groupId = "1";
    final var groupName = "group";
    final var groupRecord = new GroupRecord().setGroupId(groupId).setName(groupName);
    groupState.create(groupRecord);

    final var updatedDescription = "updatedDescription";
    groupRecord.setDescription(updatedDescription);

    // when
    groupState.update(groupRecord);

    // then
    final var group = groupState.get(groupId);
    assertThat(group.isPresent()).isTrue();
    final var persistedGroup = group.get();
    assertThat(persistedGroup.getDescription()).isEqualTo(updatedDescription);
    assertThat(persistedGroup.getGroupId()).isEqualTo(groupId);
    assertThat(persistedGroup.getName()).isEqualTo(groupName);
  }

  @Test
  void shouldDeleteGroup() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var groupName = "group";
    final var groupRecord = new GroupRecord().setGroupId(groupId).setName(groupName);
    groupState.create(groupRecord);

    // when
    groupState.delete(groupId);

    // then
    final var group = groupState.get(groupId);
    assertThat(group).isEmpty();
  }

  @Test
  void shouldReturnNewCopiesOnGet() {
    // given
    final String id = "id";
    groupState.create(new GroupRecord().setGroupId(id).setGroupKey(123L).setName("name"));

    // when
    final var group1 = groupState.get(id).get();
    final var group2 = groupState.get(id).get();

    // then
    assertThat(group1).isNotSameAs(group2);
  }

  @Test
  void shouldIterateOverAllGroups() {
    // given
    final var group1 = new GroupRecord().setGroupId("1").setName("group1");
    final var group2 = new GroupRecord().setGroupId("2").setName("group2");
    final var group3 = new GroupRecord().setGroupId("3").setName("group3");
    groupState.create(group1);
    groupState.create(group2);
    groupState.create(group3);

    // when
    final var visitedGroups = new java.util.ArrayList<String>();
    groupState.forEachGroup(
        (id, group) -> {
          visitedGroups.add(id);
          return true;
        });

    // then
    assertThat(visitedGroups).containsExactlyInAnyOrder("1", "2", "3");
  }

  @Test
  void shouldStopIterationWhenCallbackReturnsFalse() {
    // given
    final var group1 = new GroupRecord().setGroupId("1").setName("group1");
    final var group2 = new GroupRecord().setGroupId("2").setName("group2");
    final var group3 = new GroupRecord().setGroupId("3").setName("group3");
    groupState.create(group1);
    groupState.create(group2);
    groupState.create(group3);

    // when
    final var visitedGroups = new java.util.ArrayList<String>();
    groupState.forEachGroup(
        (id, group) -> {
          visitedGroups.add(id);
          return visitedGroups.size() < 2;
        });

    // then
    assertThat(visitedGroups).hasSize(2);
  }

  @Test
  void shouldFindGroupById() {
    // given
    final var groupId = "test-id";
    final var groupName = "test-name";
    final var groupRecord = new GroupRecord().setGroupId(groupId).setName(groupName);
    groupState.create(groupRecord);

    // when
    final var result = groupState.findByIdOrName(groupId);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getGroupId()).isEqualTo(groupId);
    assertThat(result.get(0).getName()).isEqualTo(groupName);
  }

  @Test
  void shouldFindGroupByName() {
    // given
    final var groupId = "test-id";
    final var groupName = "test-name";
    final var groupRecord = new GroupRecord().setGroupId(groupId).setName(groupName);
    groupState.create(groupRecord);

    // when
    final var result = groupState.findByIdOrName(groupName);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getGroupId()).isEqualTo(groupId);
    assertThat(result.get(0).getName()).isEqualTo(groupName);
  }

  @Test
  void shouldFindMultipleGroupsWithSameName() {
    // given
    final var sharedName = "shared-name";
    final var group1 = new GroupRecord().setGroupId("id1").setName(sharedName);
    final var group2 = new GroupRecord().setGroupId("id2").setName(sharedName);
    final var group3 = new GroupRecord().setGroupId("id3").setName("different-name");
    groupState.create(group1);
    groupState.create(group2);
    groupState.create(group3);

    // when
    final var result = groupState.findByIdOrName(sharedName);

    // then
    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(PersistedGroup::getGroupId)
        .containsExactlyInAnyOrder("id1", "id2");
    assertThat(result).allMatch(group -> group.getName().equals(sharedName));
  }

  @Test
  void shouldReturnEmptyListWhenGroupNotFound() {
    // given
    final var groupRecord = new GroupRecord().setGroupId("existing-id").setName("existing-name");
    groupState.create(groupRecord);

    // when
    final var result = groupState.findByIdOrName("non-existent");

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldPrioritizeIdOverNameInFindByIdOrName() {
    // given
    final var groupId = "same-value";
    final var group1 = new GroupRecord().setGroupId(groupId).setName("name1");
    final var group2 = new GroupRecord().setGroupId("id2").setName(groupId);
    groupState.create(group1);
    groupState.create(group2);

    // when
    final var result = groupState.findByIdOrName(groupId);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getGroupId()).isEqualTo(groupId);
    assertThat(result.get(0).getName()).isEqualTo("name1");
  }
}
