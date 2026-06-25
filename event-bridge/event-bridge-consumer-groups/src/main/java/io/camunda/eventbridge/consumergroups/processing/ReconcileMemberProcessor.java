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
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the internal {@code RECONCILE_MEMBER} command the coordinator appends when a member's
 * heartbeat shows it has converged to the current target. It emits {@code MEMBER_RECONCILED} when
 * the report is still valid, else a rejection (no reply — the command carries no request). It is
 * dropped when:
 *
 * <ul>
 *   <li>the group or member no longer exists;
 *   <li>the reported group epoch no longer matches (a newer rebalance superseded it);
 *   <li>the member has already reconciled to this epoch (idempotent — the heartbeat may re-send).
 * </ul>
 */
public final class ReconcileMemberProcessor implements TypedRecordProcessor<MembershipRecord> {

  private final Writers writers;
  private final ConsumerGroupState state;

  public ReconcileMemberProcessor(final Writers writers, final ConsumerGroupState state) {
    this.writers = writers;
    this.state = state;
  }

  @Override
  public void processRecord(final TypedRecord<MembershipRecord> command) {
    final var cmd = command.getValue();
    final var group = state.getGroup(cmd.getGroupId());
    if (group == null) {
      reject(command, "group no longer exists");
      return;
    }
    final var member = state.getMember(cmd.getGroupId(), cmd.getMemberId());
    if (member == null) {
      reject(
          command,
          "member '%s' is not in group '%s'".formatted(cmd.getMemberId(), cmd.getGroupId()));
      return;
    }
    if (cmd.getGroupEpoch() != group.getGroupEpoch()) {
      reject(
          command,
          "stale reconcile: reported epoch %d != group epoch %d"
              .formatted(cmd.getGroupEpoch(), group.getGroupEpoch()));
      return;
    }
    if (member.getAssignedEpoch() == group.getGroupEpoch()) {
      reject(command, "member already reconciled to epoch %d".formatted(group.getGroupEpoch()));
      return;
    }

    writers.state().appendFollowUpEvent(command.getKey(), CoordinatorIntent.MEMBER_RECONCILED, cmd);
  }

  private void reject(final TypedRecord<MembershipRecord> command, final String reason) {
    // Internal command (no request id): record the rejection in the log; there is no reply.
    writers.rejection().appendRejection(command, RejectionType.INVALID_STATE, reason);
  }
}
