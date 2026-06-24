/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.processing;

import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.state.immutable.ConsumerGroupState;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupResponse;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the {@code JOIN_GROUP} command. Like every command processor here it always results in a
 * follow-up event (on success) or a rejection (on failure), then replies to the request.
 *
 * <p>A successful join is always a <em>new</em> member: it bumps the group epoch, sets the member
 * epoch to it, and appends a {@code MEMBER_JOINED} event. A static member whose {@code
 * group.instance.id} is already held by a live member is rejected upstream by {@link
 * CoordinationChecks#validateJoin} with {@code UNRELEASED_INSTANCE_ID} (KIP-848 fences the new
 * joiner); the incumbent's slot is freed only when it leaves or the eviction loop expires it. There
 * is therefore no idempotent "rejoin" path — and no no-op event.
 *
 * <p>The reply is {@code REBALANCE_IN_PROGRESS}: the member learns its assignment from the
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
        .ifRightOrLeft(
            partitionCount -> join(command, partitionCount),
            rejection -> reject(command, rejection));
  }

  private void join(final TypedRecord<MembershipRecord> command, final int partitionCount) {
    final var cmd = command.getValue();
    final var group = state.getGroup(cmd.getGroupId());
    final var newGroupEpoch = (group == null ? 0 : group.getGroupEpoch()) + 1;
    appendMemberJoined(
        command,
        cmd.getMemberId(),
        cmd.getInstanceId(),
        newGroupEpoch,
        newGroupEpoch,
        partitionCount);
    respondJoined(command, cmd.getMemberId(), newGroupEpoch);
  }

  private void appendMemberJoined(
      final TypedRecord<MembershipRecord> command,
      final String memberId,
      final String instanceId,
      final long memberEpoch,
      final long groupEpoch,
      final int partitionCount) {
    final var event =
        new MembershipRecord()
            .setGroupId(command.getValue().getGroupId())
            .setTopic(command.getValue().getTopic())
            .setMemberId(memberId)
            .setInstanceId(instanceId)
            .setMemberEpoch(memberEpoch)
            .setGroupEpoch(groupEpoch)
            .setPartitionCount(partitionCount);
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
