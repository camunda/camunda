/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.appliers;

import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.state.group.GroupState;
import io.camunda.eventbridge.consumergroups.state.group.MemberState;
import io.camunda.eventbridge.consumergroups.state.mutable.MutableConsumerGroupState;
import io.camunda.eventbridge.stream.TypedEventApplier;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;
import java.util.List;

/**
 * Applies {@code MEMBER_JOINED}: creates the group on first join (or revives a retained one),
 * writes the epoch/state/emptySince the {@code JoinGroupProcessor} resolved onto the event, and
 * adds the new member (owning nothing until the assignor gives it a target). A join always carries
 * a fresh member id — a duplicate {@code group.instance.id} is rejected before reaching here — so
 * this only ever adds a member, never re-states an existing one. Runs identically on leader (after
 * {@code JoinGroupProcessor}) and follower (on replay).
 */
public final class MemberJoinedApplier
    implements TypedEventApplier<CoordinatorIntent, MembershipRecord> {

  private final MutableConsumerGroupState state;

  public MemberJoinedApplier(final MutableConsumerGroupState state) {
    this.state = state;
  }

  @Override
  public void applyState(final long key, final MembershipRecord value) {
    final var groupId = value.getGroupId();

    var group = state.getGroup(groupId);
    final var oldDueAt = group == null ? 0L : group.getRebalanceDueAt();
    if (group == null) {
      group = new GroupState().setSubscriptions(value.getSubscriptions()).setAssignmentEpoch(0);
    }
    group.setGroupEpoch(value.getGroupEpoch());
    group.setState(value.getState());
    group.setEmptySince(value.getEmptySince());
    group.setRebalanceDueAt(value.getRebalanceDueAt());
    state.putGroup(groupId, group);

    // A joined group is PREPARING_REBALANCE: reflect its (debounced) due deadline into the
    // rebalance
    // index, and drop it from the empty index in case this join revived a retained EMPTY group.
    Appliers.reindexRebalanceDue(state, groupId, oldDueAt, value.getRebalanceDueAt());
    state.untrackEmpty(groupId);

    final var member =
        new MemberState()
            .setInstanceId(value.getInstanceId())
            .setMemberEpoch(value.getMemberEpoch())
            .setTargetPartitions(List.of());
    state.putMember(groupId, value.getMemberId(), member);
  }
}
