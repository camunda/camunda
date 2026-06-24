/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.processing;

import io.camunda.eventbridge.consumergroups.record.CoordinatorIntent;
import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.state.immutable.ConsumerGroupState;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupResponse;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the {@code JOIN_GROUP} command. Like every command processor here it always results in a
 * follow-up event (on success) or a rejection (on failure), then replies to the request:
 *
 * <ul>
 *   <li><b>Static member, already known</b>: an idempotent rejoin — appends a {@code MEMBER_JOINED}
 *       event that re-states the existing member id + epoch at the <em>unchanged</em> group epoch
 *       (so the applier is a no-op that preserves the member's target and triggers no rebalance),
 *       and replies with the current identity. Writing the event keeps the "every command yields an
 *       event or a rejection" invariant, and a retried join or a restart within the session timeout
 *       does not cause a "perpetual rejoin storm".
 *   <li><b>New member</b> (dynamic, or a static member whose record was evicted): bumps the group
 *       epoch, sets the member epoch to it, and appends a {@code MEMBER_JOINED} event.
 * </ul>
 *
 * Either way the reply is {@code REBALANCE_IN_PROGRESS}: the member learns its assignment from the
 * subsequent heartbeats once the async assignor has computed a target.
 */
public final class JoinGroupProcessor implements TypedRecordProcessor<MembershipRecord> {

  private final Writers writers;
  private final ConsumerGroupState state;
  private final CoordinationChecks checks;

  public JoinGroupProcessor(
      final Writers writers, final ConsumerGroupState state, final CoordinationChecks checks) {
    this.writers = writers;
    this.state = state;
    this.checks = checks;
  }

  @Override
  public void processRecord(final TypedRecord<MembershipRecord> command) {
    checks
        .validateJoin(command.getValue())
        .ifRightOrLeft(ok -> join(command), rejection -> reject(command, rejection));
  }

  private void join(final TypedRecord<MembershipRecord> command) {
    final var cmd = command.getValue();
    final var groupId = cmd.getGroupId();
    final var instanceId = cmd.getInstanceId();

    if (instanceId != null) {
      final var existingMemberId = state.findMemberByInstanceId(groupId, instanceId);
      if (existingMemberId != null) {
        // Idempotent static rejoin — re-state the member at the current group epoch (no bump).
        final var existing = state.getMember(groupId, existingMemberId);
        final var groupEpoch = state.getGroup(groupId).getGroupEpoch();
        appendMemberJoined(
            command, existingMemberId, instanceId, existing.getMemberEpoch(), groupEpoch);
        respondJoined(command, existingMemberId, existing.getMemberEpoch());
        return;
      }
    }

    final var group = state.getGroup(groupId);
    final var newGroupEpoch = (group == null ? 0 : group.getGroupEpoch()) + 1;
    appendMemberJoined(command, cmd.getMemberId(), instanceId, newGroupEpoch, newGroupEpoch);
    respondJoined(command, cmd.getMemberId(), newGroupEpoch);
  }

  private void appendMemberJoined(
      final TypedRecord<MembershipRecord> command,
      final String memberId,
      final String instanceId,
      final long memberEpoch,
      final long groupEpoch) {
    final var event =
        new MembershipRecord()
            .setGroupId(command.getValue().getGroupId())
            .setMemberId(memberId)
            .setInstanceId(instanceId)
            .setMemberEpoch(memberEpoch)
            .setGroupEpoch(groupEpoch)
            .setPartitionCount(command.getValue().getPartitionCount());
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
