/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.processing;

import io.camunda.eventbridge.consumergroups.record.RebalanceRecord;
import io.camunda.eventbridge.consumergroups.state.group.GroupLifecycle;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the internal {@code REBALANCE_GROUP} command emitted by the async assignor. {@link
 * TransitionValidator#validateRebalance} decides whether the proposed target is still valid (group
 * exists, computed for the current epoch, not already applied); on success this appends a {@code
 * GROUP_REBALANCED} event carrying the resolved group state, otherwise a {@code COMMAND_REJECTION}
 * with the reason (the command carries no request, so there is no reply).
 */
public final class RebalanceProcessor implements TypedRecordProcessor<RebalanceRecord> {

  private final Writers writers;
  private final TransitionValidator validator;

  public RebalanceProcessor(final Writers writers, final TransitionValidator validator) {
    this.writers = writers;
    this.validator = validator;
  }

  @Override
  public void processRecord(final TypedRecord<RebalanceRecord> command) {
    validator
        .validateRebalance(command.getValue())
        .ifRightOrLeft(ok -> rebalance(command), reason -> reject(command, reason));
  }

  private void rebalance(final TypedRecord<RebalanceRecord> command) {
    // A committed target lands the group in RECONCILING until members confirm — decided here, not
    // in the applier.
    final var cmd = command.getValue();
    cmd.setState(GroupLifecycle.RECONCILING);
    writers.state().appendFollowUpEvent(command.getKey(), CoordinatorIntent.GROUP_REBALANCED, cmd);
  }

  private void reject(final TypedRecord<RebalanceRecord> command, final String reason) {
    // Internal command (no request id) — record the rejection in the log; there is no reply.
    writers.rejection().appendRejection(command, RejectionType.INVALID_STATE, reason);
  }
}
