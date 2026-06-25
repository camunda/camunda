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
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the internal {@code RECONCILE_MEMBER} command the coordinator appends when a member's
 * heartbeat shows it has converged to the current target. {@link
 * TransitionValidator#validateReconcile} decides whether the report is still valid (group/member
 * exist, current epoch, not already reconciled); on success this emits {@code MEMBER_RECONCILED}
 * carrying the resolved group state, otherwise a {@code COMMAND_REJECTION} with the reason (no
 * reply — the command carries no request).
 */
public final class ReconcileMemberProcessor implements TypedRecordProcessor<MembershipRecord> {

  private final Writers writers;
  private final ConsumerGroupState state;
  private final TransitionValidator validator;

  public ReconcileMemberProcessor(
      final Writers writers, final ConsumerGroupState state, final TransitionValidator validator) {
    this.writers = writers;
    this.state = state;
    this.validator = validator;
  }

  @Override
  public void processRecord(final TypedRecord<MembershipRecord> command) {
    validator
        .validateReconcile(command.getValue())
        .ifRightOrLeft(ok -> reconcile(command), reason -> reject(command, reason));
  }

  private void reconcile(final TypedRecord<MembershipRecord> command) {
    final var cmd = command.getValue();
    // Decide the resulting lifecycle here (not in the applier): once this member advances to the
    // group epoch (validated as the current one), the group is STABLE iff every other member is
    // already at it, else RECONCILING.
    final var members = state.groupSnapshot(cmd.getGroupId()).members();
    final var becomesStable =
        members.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(cmd.getMemberId()))
            .allMatch(entry -> entry.getValue().assignedEpoch() == cmd.getGroupEpoch());
    cmd.setState(becomesStable ? GroupLifecycle.STABLE : GroupLifecycle.RECONCILING);

    writers.state().appendFollowUpEvent(command.getKey(), CoordinatorIntent.MEMBER_RECONCILED, cmd);
  }

  private void reject(final TypedRecord<MembershipRecord> command, final String reason) {
    // Internal command (no request id): record the rejection in the log; there is no reply.
    writers.rejection().appendRejection(command, RejectionType.INVALID_STATE, reason);
  }
}
