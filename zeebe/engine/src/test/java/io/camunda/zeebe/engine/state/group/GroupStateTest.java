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
    final var groupKey = 1L;
    final var groupName = "group";
    final var groupRecord = new GroupRecord().setGroupKey(groupKey).setName(groupName);

    // when
    groupState.create(groupKey, groupRecord);

    // then
    final var group = groupState.get(groupKey);
    assertThat(group.isPresent()).isTrue();
    final var persistedGroup = group.get();
    assertThat(persistedGroup.getGroupKey()).isEqualTo(groupKey);
    assertThat(persistedGroup.getName()).isEqualTo(groupName);
  }

  @Test
  void shouldReturnKeyForGroupName() {
    // given
    final var groupKey = 1L;
    final var groupName = "group";
    final var groupRecord = new GroupRecord().setGroupKey(groupKey).setName(groupName);
    groupState.create(groupKey, groupRecord);

    // when
    final var key = groupState.getGroupKeyByName(groupName);

    // then
    assertThat(key.isPresent()).isTrue();
    assertThat(key.get()).isEqualTo(groupKey);
  }

  @Test
  void shouldReturnNullIfGroupDoesNotExist() {
    // given
    final var groupKey = 2L;

    // when
    final var group = groupState.get(groupKey);

    // then
    assertThat(group.isPresent()).isFalse();
  }

  @Test
  void shouldReturnNullIfNameDoesNotExist() {
    // given
    final var groupName = "group";

    // when
    final var key = groupState.getGroupKeyByName(groupName);

    // then
    assertThat(key.isPresent()).isFalse();
  }

  @Test
  void shouldUpdateGroup() {
    // given
    final var groupKey = 1L;
    final var groupName = "group";
    final var groupRecord = new GroupRecord().setGroupKey(groupKey).setName(groupName);
    groupState.create(groupKey, groupRecord);

    final var updatedGroupName = "updatedGroup";
    groupRecord.setName(updatedGroupName);

    // when
    groupState.update(groupKey, groupRecord);

    // then
    var group = groupState.get(groupKey);
    assertThat(group.isPresent()).isTrue();
    var persistedGroup = group.get();
    assertThat(persistedGroup.getGroupKey()).isEqualTo(groupKey);
    assertThat(persistedGroup.getName()).isEqualTo(updatedGroupName);

    final var groupKeyByName = groupState.getGroupKeyByName(updatedGroupName);
    assertThat(groupKeyByName.isPresent()).isTrue();
    group = groupState.get(groupKeyByName.get());
    assertThat(group.isPresent()).isTrue();
    persistedGroup = group.get();
    assertThat(persistedGroup.getGroupKey()).isEqualTo(groupKey);
    assertThat(persistedGroup.getName()).isEqualTo(updatedGroupName);
  }
}
