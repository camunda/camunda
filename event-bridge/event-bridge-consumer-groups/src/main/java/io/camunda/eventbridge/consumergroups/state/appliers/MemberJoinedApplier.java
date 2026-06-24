/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.appliers;

import io.camunda.eventbridge.consumergroups.record.CoordinatorIntent;
import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.state.group.GroupState;
import io.camunda.eventbridge.consumergroups.state.group.MemberState;
import io.camunda.eventbridge.consumergroups.state.mutable.MutableConsumerGroupState;
import io.camunda.eventbridge.stream.TypedEventApplier;
import java.util.List;

/**
 * Applies {@code MEMBER_JOINED}: creates the group on first join, sets its (possibly unchanged)
 * group epoch, and adds the member to the roster. Idempotent — a re-applied join for an existing
 * member (a static rejoin) preserves that member's current target assignment rather than resetting
 * it. The decision logic lives here; {@link MutableConsumerGroupState} only does granular put/get.
 * Runs identically on leader (after {@code JoinGroupProcessor}) and follower (on replay).
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
      group = new GroupState().setPartitionCount(value.getPartitionCount()).setAssignmentEpoch(0);
    }
    group.setGroupEpoch(value.getGroupEpoch());
    state.putGroup(groupId, group);

    final var existing = state.getMember(groupId, value.getMemberId());
    final var member = existing == null ? new MemberState() : existing;
    member.setInstanceId(value.getInstanceId()).setMemberEpoch(value.getMemberEpoch());
    if (existing == null) {
      member.setTargetPartitions(List.of());
    }
    state.putMember(groupId, value.getMemberId(), member);
  }
}
