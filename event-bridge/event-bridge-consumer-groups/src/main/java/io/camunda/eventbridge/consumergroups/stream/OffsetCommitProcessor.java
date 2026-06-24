/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.stream;

import io.camunda.eventbridge.protocol.request.coordination.CommitOffsetResponse;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the {@code COMMIT_OFFSET} command. Validation (delegated to {@link
 * OffsetCommitValidator}) happens here, not in the manager: the commit is fenced against the
 * replicated group metadata. On success it appends an {@code OFFSET_COMMITTED} event (applied by
 * {@link OffsetCommittedApplier}, monotonically) and replies with the resulting committed position;
 * on failure it replies with the error code and writes no event. Either way the reply is staged via
 * the response writer and flushed only after the command commits.
 */
public final class OffsetCommitProcessor implements TypedRecordProcessor<OffsetCommitRecord> {

  private final Writers writers;
  private final OffsetState offsetState;
  private final OffsetCommitValidator validator;

  public OffsetCommitProcessor(
      final Writers writers, final OffsetState offsetState, final OffsetCommitValidator validator) {
    this.writers = writers;
    this.offsetState = offsetState;
    this.validator = validator;
  }

  @Override
  public void processRecord(final TypedRecord<OffsetCommitRecord> command) {
    final var cmd = command.getValue();

    final var error = validator.validate(cmd);
    if (error != CoordinationErrorCode.NONE) {
      writers.response().respond(command, new CommitOffsetResponse().setErrorCode(error));
      return;
    }

    final var event =
        new OffsetCommitRecord()
            .setGroupId(cmd.getGroupId())
            .setPartitionId(cmd.getPartitionId())
            .setOffset(cmd.getOffset());
    writers
        .state()
        .appendFollowUpEvent(command.getKey(), CoordinatorIntent.OFFSET_COMMITTED, event);

    final var committed = offsetState.getOffset(cmd.getGroupId(), cmd.getPartitionId());
    writers
        .response()
        .respond(
            command,
            new CommitOffsetResponse()
                .setErrorCode(CoordinationErrorCode.NONE)
                .setCommittedPosition(committed));
  }
}
