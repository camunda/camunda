/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.processing;

import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.state.group.GroupLifecycle;
import io.camunda.eventbridge.consumergroups.state.immutable.ConsumerGroupState;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupResponse;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.time.Duration;
import java.util.Map;

/**
 * Handles the {@code JOIN_GROUP} command. Like every command processor here it always results in a
 * follow-up event (on success) or a rejection (on failure), then replies to the request.
 *
 * <p>A join is either a <em>normal</em> join (a fresh member id or a static instance id whose
 * previous holder already left/was evicted) or, per {@link CoordinationValidator#validateJoin}'s
 * classification, a static-membership <b>takeover</b> — the {@code group.instance.id} is already
 * held by a live roster member. A normal join bumps the group epoch, sets the member epoch to it,
 * and appends a {@code MEMBER_JOINED} event (a brand-new member). A takeover instead {@link
 * #takeover appends} a {@code MEMBER_TAKEN_OVER} event that reuses the incumbent's memberId with a
 * strictly higher memberEpoch and leaves the group untouched — no epoch bump, no rebalance; the
 * incumbent's target/assigned partitions are inherited verbatim because the same row is kept, and
 * every subsequent command presenting the incumbent's now-superseded (memberId, memberEpoch) is
 * fenced by the existing epoch check ({@code CoordinationValidator#epochUpToDate}, and identically
 * in the heartbeat handler) with no further change needed there.
 *
 * <p>The reply is {@code REBALANCE_IN_PROGRESS}: the member learns its assignment from the
 * subsequent heartbeats once the async assignor has computed a target (unaffected by whether the
 * assignor actually runs, which it does not for a takeover).
 */
public final class JoinGroupProcessor implements TypedRecordProcessor<MembershipRecord> {

  private final Writers writers;
  private final ConsumerGroupState state;
  private final CoordinationValidator validator;
  private final Duration rebalanceDebounce;

  public JoinGroupProcessor(
      final Writers writers,
      final ConsumerGroupState state,
      final CoordinationValidator validator,
      final Duration rebalanceDebounce) {
    this.writers = writers;
    this.state = state;
    this.validator = validator;
    this.rebalanceDebounce = rebalanceDebounce;
  }

  @Override
  public void processRecord(final TypedRecord<MembershipRecord> command) {
    validator
        .validateJoin(command.getValue())
        .ifRightOrLeft(
            classification -> {
              if (classification.isTakeover()) {
                takeover(command, classification.takeoverOfMemberId());
              } else {
                join(command, classification.subscriptions());
              }
            },
            rejection -> reject(command, rejection));
  }

  /**
   * Replaces the incumbent's incarnation in place: the SAME memberId row is kept (only its
   * memberEpoch changes), so its target/assigned partitions are inherited verbatim with no extra
   * code, the group's epoch/state/rebalance-due bookkeeping is untouched (no rebalance), and the
   * existing epoch-fencing check rejects the superseded incarnation's next command for free.
   */
  private void takeover(final TypedRecord<MembershipRecord> command, final String memberId) {
    final var cmd = command.getValue();
    final var groupId = cmd.getGroupId();
    final var incumbent = state.getMember(groupId, memberId);
    final var newMemberEpoch = incumbent.getMemberEpoch() + 1;
    final var event =
        new MembershipRecord()
            .setGroupId(groupId)
            .setMemberId(memberId)
            .setInstanceId(cmd.getInstanceId())
            .setMemberEpoch(newMemberEpoch);
    writers
        .state()
        .appendFollowUpEvent(command.getKey(), CoordinatorIntent.MEMBER_TAKEN_OVER, event);
    respondJoined(command, memberId, newMemberEpoch);
  }

  private void join(
      final TypedRecord<MembershipRecord> command, final Map<String, Integer> subscriptions) {
    final var cmd = command.getValue();
    final var group = state.getGroup(cmd.getGroupId());
    final var newGroupEpoch = (group == null ? 0 : group.getGroupEpoch()) + 1;
    // The join makes the target stale, so the group needs a (debounced) rebalance; keep an existing
    // deadline if it is already pending, else open the window now.
    final var rebalanceDueAt =
        RebalanceDebounce.dueAt(group, command.getTimestamp(), rebalanceDebounce);
    appendMemberJoined(
        command,
        cmd.getMemberId(),
        cmd.getInstanceId(),
        newGroupEpoch,
        newGroupEpoch,
        subscriptions,
        rebalanceDueAt,
        cmd.getStandbyReplicas());
    respondJoined(command, cmd.getMemberId(), newGroupEpoch);
  }

  private void appendMemberJoined(
      final TypedRecord<MembershipRecord> command,
      final String memberId,
      final String instanceId,
      final long memberEpoch,
      final long groupEpoch,
      final Map<String, Integer> subscriptions,
      final long rebalanceDueAt,
      final int standbyReplicas) {
    final var event =
        new MembershipRecord()
            .setGroupId(command.getValue().getGroupId())
            .setSubscriptions(subscriptions)
            .setMemberId(memberId)
            .setInstanceId(instanceId)
            .setMemberEpoch(memberEpoch)
            .setGroupEpoch(groupEpoch)
            // A join always lands the group in PREPARING_REBALANCE (target now stale) and clears
            // any
            // retention deadline a revived EMPTY group carried.
            .setState(GroupLifecycle.PREPARING_REBALANCE)
            .setEmptySince(0L)
            .setRebalanceDueAt(rebalanceDueAt)
            // Fixed at group creation only (the applier ignores this on a join to an existing
            // group); carried on every join regardless so the first join of a group always
            // supplies it.
            .setStandbyReplicas(standbyReplicas);
    writers.state().appendFollowUpEvent(command.getKey(), CoordinatorIntent.MEMBER_JOINED, event);
  }

  private void respondJoined(
      final TypedRecord<MembershipRecord> command, final String memberId, final long memberEpoch) {
    writers
        .response()
        .respond(
            command,
            new JoinGroupResponse()
                .setErrorCode(CoordinationErrorCode.REBALANCE_IN_PROGRESS)
                .setMemberId(memberId)
                .setMemberEpoch(memberEpoch));
  }

  private void reject(final TypedRecord<MembershipRecord> command, final Rejection rejection) {
    writers.rejection().appendRejection(command, rejection.rejectionType(), rejection.reason());
    writers.response().writeRejection(command, rejection.rejectionType(), rejection.reason());
  }
}
