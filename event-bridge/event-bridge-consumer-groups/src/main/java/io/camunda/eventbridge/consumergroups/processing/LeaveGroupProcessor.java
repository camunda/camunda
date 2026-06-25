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
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupResponse;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the {@code LEAVE_GROUP} command — sent by a leaving member, and also written by the
 * coordinator when it evicts a timed-out member. On success it appends a {@code MEMBER_LEFT} event
 * (the applier removes the member and bumps the group epoch) and replies {@code NONE}; on failure
 * it appends a rejection and replies with the error code. The reply is a no-op for an
 * eviction-driven leave (no waiting request).
 */
public final class LeaveGroupProcessor implements TypedRecordProcessor<MembershipRecord> {

  private final Writers writers;
  private final ConsumerGroupState state;
  private final CoordinationValidator validator;

  public LeaveGroupProcessor(
      final Writers writers,
      final ConsumerGroupState state,
      final CoordinationValidator validator) {
    this.writers = writers;
    this.state = state;
    this.validator = validator;
  }

  @Override
  public void processRecord(final TypedRecord<MembershipRecord> command) {
    validator
        .validateLeave(command.getValue())
        .ifRightOrLeft(member -> leave(command), rejection -> reject(command, rejection));
  }

  private void leave(final TypedRecord<MembershipRecord> command) {
    final var cmd = command.getValue();
    final var newGroupEpoch = state.getGroup(cmd.getGroupId()).getGroupEpoch() + 1;

    // Decide the resulting lifecycle here (not in the applier): the group becomes EMPTY iff the
    // leaver is its only member, else it drops back to PREPARING_REBALANCE for the assignor.
    final var members = state.groupSnapshot(cmd.getGroupId()).members();
    final var becomesEmpty = members.size() == 1 && members.containsKey(cmd.getMemberId());
    final var resultingState =
        becomesEmpty ? GroupLifecycle.EMPTY : GroupLifecycle.PREPARING_REBALANCE;
    // Stamp the retention deadline base from the command's (replicated) processing time when the
    // group empties — identical on every replica, so it survives failover; 0 otherwise.
    final var emptySince = becomesEmpty ? command.getTimestamp() : 0L;

    final var event =
        new MembershipRecord()
            .setGroupId(cmd.getGroupId())
            .setMemberId(cmd.getMemberId())
            .setGroupEpoch(newGroupEpoch)
            .setState(resultingState)
            .setEmptySince(emptySince);
    writers.state().appendFollowUpEvent(command.getKey(), CoordinatorIntent.MEMBER_LEFT, event);
    writers
        .response()
        .respond(command, new LeaveGroupResponse().setErrorCode(CoordinationErrorCode.NONE));
  }

  private void reject(final TypedRecord<MembershipRecord> command, final Rejection rejection) {
    writers.rejection().appendRejection(command, rejection.rejectionType(), rejection.reason());
    writers.response().writeRejection(command, rejection.rejectionType(), rejection.reason());
  }
}
