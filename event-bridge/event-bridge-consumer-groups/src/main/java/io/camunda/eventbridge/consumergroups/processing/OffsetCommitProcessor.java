/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.processing;

import io.camunda.eventbridge.consumergroups.record.OffsetCommitRecord;
import io.camunda.eventbridge.consumergroups.state.immutable.OffsetState;
import io.camunda.eventbridge.protocol.request.coordination.CommitOffsetResponse;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the {@code COMMIT_OFFSET} command. On success it appends an {@code OFFSET_COMMITTED}
 * event (applied monotonically by {@code OffsetCommittedApplier}) and replies with the resulting
 * committed position; on failure (fenced against replicated membership by {@link
 * CoordinationValidator}) it appends a rejection and replies with the error code.
 */
public final class OffsetCommitProcessor implements TypedRecordProcessor<OffsetCommitRecord> {

  private final Writers writers;
  private final OffsetState offsetState;
  private final CoordinationValidator validator;

  public OffsetCommitProcessor(
      final Writers writers, final OffsetState offsetState, final CoordinationValidator validator) {
    this.writers = writers;
    this.offsetState = offsetState;
    this.validator = validator;
  }

  @Override
  public void processRecord(final TypedRecord<OffsetCommitRecord> command) {
    validator
        .validateCommit(command.getValue())
        .ifRightOrLeft(member -> commit(command), rejection -> reject(command, rejection));
  }

  private void commit(final TypedRecord<OffsetCommitRecord> command) {
    final var cmd = command.getValue();
    // Resolve the monotonic (never-rewind) committed position here and stamp the actual value on
    // the event, so the applier just stores it and the reply is the real committed position (a
    // read after the append would still see the pre-commit value).
    final var existing =
        offsetState.getOffset(cmd.getGroupId(), cmd.getTopic(), cmd.getPartitionId());
    final var committed = existing < 0 ? cmd.getOffset() : Math.max(existing, cmd.getOffset());
    final var event =
        new OffsetCommitRecord()
            .setGroupId(cmd.getGroupId())
            .setTopic(cmd.getTopic())
            .setPartitionId(cmd.getPartitionId())
            .setOffset(committed);
    writers
        .state()
        .appendFollowUpEvent(command.getKey(), CoordinatorIntent.OFFSET_COMMITTED, event);
    writers
        .response()
        .respond(
            command,
            new CommitOffsetResponse()
                .setErrorCode(CoordinationErrorCode.NONE)
                .setCommittedPosition(committed));
  }

  private void reject(final TypedRecord<OffsetCommitRecord> command, final Rejection rejection) {
    writers.rejection().appendRejection(command, rejection.rejectionType(), rejection.reason());
    writers.response().writeRejection(command, rejection.rejectionType(), rejection.reason());
  }
}
