/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.processing;

import io.camunda.eventbridge.consumergroups.record.CoordinatorIntent;
import io.camunda.eventbridge.consumergroups.record.RebalanceRecord;
import io.camunda.eventbridge.consumergroups.state.immutable.ConsumerGroupState;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the internal {@code REBALANCE_GROUP} command emitted by the async assignor. As with every
 * command processor here it always results in either a follow-up event or a rejection: it appends a
 * {@code GROUP_REBALANCED} event when the proposed target is still valid, otherwise it appends a
 * {@code COMMAND_REJECTION} record explaining why it was dropped (the command carries no request,
 * so there is no reply). It is dropped when:
 *
 * <ul>
 *   <li>the group no longer exists (its last member left between the assignor's read and now);
 *   <li>the group epoch has advanced past the epoch the target was computed for (stale roster);
 *   <li>the target is already applied (assignment epoch ≥ the proposed epoch — idempotent).
 * </ul>
 */
public final class RebalanceProcessor implements TypedRecordProcessor<RebalanceRecord> {

  private final Writers writers;
  private final ConsumerGroupState state;

  public RebalanceProcessor(final Writers writers, final ConsumerGroupState state) {
    this.writers = writers;
    this.state = state;
  }

  @Override
  public void processRecord(final TypedRecord<RebalanceRecord> command) {
    final var cmd = command.getValue();
    final var group = state.getGroup(cmd.getGroupId());

    if (group == null) {
      reject(command, "group no longer exists");
      return;
    }
    if (cmd.getAssignmentEpoch() != group.getGroupEpoch()) {
      reject(
          command,
          "stale roster: target epoch %d != group epoch %d"
              .formatted(cmd.getAssignmentEpoch(), group.getGroupEpoch()));
      return;
    }
    if (group.getAssignmentEpoch() >= cmd.getAssignmentEpoch()) {
      reject(command, "target epoch %d already applied".formatted(cmd.getAssignmentEpoch()));
      return;
    }

    writers.state().appendFollowUpEvent(command.getKey(), CoordinatorIntent.GROUP_REBALANCED, cmd);
  }

  private void reject(final TypedRecord<RebalanceRecord> command, final String reason) {
    // Internal command (no request id) — record the rejection in the log; there is no reply.
    writers.rejection().appendRejection(command, RejectionType.INVALID_STATE, reason);
  }
}
