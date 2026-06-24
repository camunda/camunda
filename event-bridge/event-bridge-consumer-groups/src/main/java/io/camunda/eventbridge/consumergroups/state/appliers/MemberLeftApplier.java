/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.appliers;

import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.state.mutable.MutableConsumerGroupState;
import io.camunda.eventbridge.stream.TypedEventApplier;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;

/**
 * Applies {@code MEMBER_LEFT}: removes the member and either deletes the group once its last member
 * leaves, or bumps the group epoch so the assignor recomputes the target. The decision logic lives
 * here. Runs identically on leader (after {@code LeaveGroupProcessor} or an eviction) and follower
 * (on replay).
 */
public final class MemberLeftApplier
    implements TypedEventApplier<CoordinatorIntent, MembershipRecord> {

  private final MutableConsumerGroupState state;

  public MemberLeftApplier(final MutableConsumerGroupState state) {
    this.state = state;
  }

  @Override
  public void applyState(final long key, final MembershipRecord value) {
    final var groupId = value.getGroupId();
    state.deleteMember(groupId, value.getMemberId());

    if (state.isGroupEmpty(groupId)) {
      state.deleteGroup(groupId);
      return;
    }

    final var group = state.getGroup(groupId);
    if (group != null) {
      group.setGroupEpoch(value.getGroupEpoch());
      state.putGroup(groupId, group);
    }
  }
}
