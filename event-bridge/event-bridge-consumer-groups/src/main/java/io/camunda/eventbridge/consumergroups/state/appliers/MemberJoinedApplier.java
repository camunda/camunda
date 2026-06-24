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
import io.camunda.eventbridge.consumergroups.state.group.GroupState;
import io.camunda.eventbridge.consumergroups.state.group.MemberState;
import io.camunda.eventbridge.consumergroups.state.mutable.MutableConsumerGroupState;
import io.camunda.eventbridge.stream.TypedEventApplier;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;
import java.util.List;

/**
 * Applies {@code MEMBER_JOINED}: creates the group on first join, bumps its group epoch, and adds
 * the new member (owning nothing until the assignor gives it a target). A join always carries a
 * fresh member id — a duplicate {@code group.instance.id} is rejected before reaching here — so
 * this only ever adds a member, never re-states an existing one. The decision logic lives here;
 * {@link MutableConsumerGroupState} only does granular put/get. Runs identically on leader (after
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
    if (group == null) {
      group = new GroupState().setSubscriptions(value.getSubscriptions()).setAssignmentEpoch(0);
    }
    group.setGroupEpoch(value.getGroupEpoch());
    // A join bumps the group epoch, so the target is now stale until the assignor reruns: the
    // group enters PREPARING_REBALANCE (a new group's first state too).
    group.setState(GroupLifecycle.PREPARING_REBALANCE);
    state.putGroup(groupId, group);

    final var member =
        new MemberState()
            .setInstanceId(value.getInstanceId())
            .setMemberEpoch(value.getMemberEpoch())
            .setTargetPartitions(List.of());
    state.putMember(groupId, value.getMemberId(), member);
  }
}
