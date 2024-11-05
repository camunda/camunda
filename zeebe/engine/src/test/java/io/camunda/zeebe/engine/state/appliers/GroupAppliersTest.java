/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutableGroupState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class GroupAppliersTest {

  private MutableProcessingState processingState;

  private MutableGroupState groupState;
  private MutableAuthorizationState authorizationState;

  @BeforeEach
  public void setup() {
    groupState = processingState.getGroupState();
    authorizationState = processingState.getAuthorizationState();
  }

  @Test
  void shouldCreateGroup() {
    // given
    final var groupCreatedApplier = new GroupCreatedApplier(groupState, authorizationState);
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
    final var groupUpdatedApplier = new GroupUpdatedApplier(groupState);
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
}
