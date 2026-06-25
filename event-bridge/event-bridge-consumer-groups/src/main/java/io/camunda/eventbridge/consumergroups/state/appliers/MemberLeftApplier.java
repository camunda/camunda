/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.appliers;

import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.state.group.GroupLifecycle;
import io.camunda.eventbridge.consumergroups.state.mutable.MutableConsumerGroupState;
import io.camunda.eventbridge.stream.TypedEventApplier;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;

/**
 * Applies {@code MEMBER_LEFT}: removes the member and either retains the now-empty group as {@code
 * EMPTY} (keeping its committed offsets so a later rejoin resumes — the group is reclaimed later by
 * the retention task, not here), or bumps the group epoch so the assignor recomputes the target.
 * The decision logic lives here. Runs identically on leader (after {@code LeaveGroupProcessor} or
 * an eviction) and follower (on replay).
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

    final var group = state.getGroup(groupId);
    if (group == null) {
      return;
    }
    group.setGroupEpoch(value.getGroupEpoch());
    if (state.isGroupEmpty(groupId)) {
      // Last member left: retain the group (and its committed offsets) as EMPTY; the retention
      // task reclaims it once it has been empty for the retention window. Stamp when it became
      // empty (from the event, so it is identical on every replica) as the retention deadline.
      group.setState(GroupLifecycle.EMPTY);
      group.setEmptySince(value.getTimestamp());
    } else {
      // A leave bumps the group epoch, so the remaining members' target is stale: back to
      // PREPARING_REBALANCE until the assignor recomputes.
      group.setState(GroupLifecycle.PREPARING_REBALANCE);
    }
    state.putGroup(groupId, group);
  }
}
