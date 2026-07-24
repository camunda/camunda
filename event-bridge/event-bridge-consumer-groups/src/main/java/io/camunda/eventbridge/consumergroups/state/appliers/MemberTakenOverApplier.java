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
 * Applies {@code MEMBER_TAKEN_OVER}: a static-membership takeover reuses the incumbent's memberId
 * row and only advances its memberEpoch to the value the {@code JoinGroupProcessor} resolved
 * (strictly greater than every epoch the incumbent ever held). Everything else on the row —
 * instanceId, targetPartitions, standbyTargetPartitions, assignedEpoch — is left exactly as it was,
 * which is what makes the inheritance verbatim: there is nothing else to copy. The group row is not
 * touched at all (no epoch bump, no lifecycle change, no rebalance-due reindex), matching the
 * invariant that a takeover never triggers a rebalance. Runs identically on leader (after {@code
 * JoinGroupProcessor}) and follower (on replay); the member always exists here (the takeover only
 * fires when the validator found a live roster member to take over from, and replay reconstructs
 * that same state in log order).
 */
public final class MemberTakenOverApplier
    implements TypedEventApplier<CoordinatorIntent, MembershipRecord> {

  private final MutableConsumerGroupState state;

  public MemberTakenOverApplier(final MutableConsumerGroupState state) {
    this.state = state;
  }

  @Override
  public void applyState(final long key, final MembershipRecord value) {
    final var groupId = value.getGroupId();
    final var memberId = value.getMemberId();
    final var member = state.getMember(groupId, memberId);
    member.setMemberEpoch(value.getMemberEpoch());
    state.putMember(groupId, memberId, member);
  }
}
