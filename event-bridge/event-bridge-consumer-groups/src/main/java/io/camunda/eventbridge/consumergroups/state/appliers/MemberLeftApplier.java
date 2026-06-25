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
 * Applies {@code MEMBER_LEFT}: removes the member and writes the epoch/state/emptySince the {@code
 * LeaveGroupProcessor} resolved onto the event — either {@code EMPTY} (retaining the group and its
 * committed offsets for a later rejoin; reclaimed later by the retention task) or {@code
 * PREPARING_REBALANCE} (the assignor recomputes the target) — and reflects that into the lifecycle
 * index. Runs identically on leader (after {@code LeaveGroupProcessor} or an eviction) and follower
 * (on replay).
 *
 * <p>The event is only appended after {@code LeaveGroupProcessor} (via {@code validateLeave}) has
 * confirmed the group and member exist, and replay reconstructs that same state in log order — so
 * the group is always present here; no guard-and-return.
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
    group.setGroupEpoch(value.getGroupEpoch());
    group.setState(value.getState());
    group.setEmptySince(value.getEmptySince());
    state.putGroup(groupId, group);

    // Reflect the resolved lifecycle into the indexes: an emptied group joins the retention index,
    // otherwise it goes back into the assignor's index.
    if (value.getState() == GroupLifecycle.EMPTY) {
      state.trackEmpty(groupId);
      state.untrackPendingRebalance(groupId);
    } else {
      state.trackPendingRebalance(groupId);
      state.untrackEmpty(groupId);
    }
  }
}
