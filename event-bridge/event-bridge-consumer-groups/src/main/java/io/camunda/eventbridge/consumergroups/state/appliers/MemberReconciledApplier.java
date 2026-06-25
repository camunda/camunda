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
 * Applies {@code MEMBER_RECONCILED}: advances the member's {@code assignedEpoch} to the group epoch
 * it confirmed, then transitions the group to {@code STABLE} once every member has reconciled to
 * the current epoch (else it stays {@code RECONCILING}). Runs identically on leader (after {@code
 * ReconcileMemberProcessor}) and follower (on replay).
 *
 * <p>The event is only appended after {@code ReconcileMemberProcessor} has validated that the group
 * and member exist (rejecting the command otherwise), and replay reconstructs that same state in
 * log order — so the applier trusts both are present rather than guarding and silently returning.
 */
public final class MemberReconciledApplier
    implements TypedEventApplier<CoordinatorIntent, MembershipRecord> {

  private final MutableConsumerGroupState state;

  public MemberReconciledApplier(final MutableConsumerGroupState state) {
    this.state = state;
  }

  @Override
  public void applyState(final long key, final MembershipRecord value) {
    final var groupId = value.getGroupId();
    final var member = state.getMember(groupId, value.getMemberId());
    member.setAssignedEpoch(value.getGroupEpoch());
    state.putMember(groupId, value.getMemberId(), member);

    final var group = state.getGroup(groupId);
    if (state.allMembersReconciled(groupId, group.getGroupEpoch())) {
      group.setState(GroupLifecycle.STABLE);
      state.putGroup(groupId, group);
    }
  }
}
