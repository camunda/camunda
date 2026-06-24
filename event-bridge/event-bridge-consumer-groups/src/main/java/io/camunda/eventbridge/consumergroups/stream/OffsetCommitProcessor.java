/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.stream;

import io.camunda.eventbridge.stream.StateWriter;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the {@code COMMIT_OFFSET} command: turns it into an {@code OFFSET_COMMITTED} follow-up
 * event, which {@link OffsetCommittedApplier} applies to the replicated offset state. Holds no
 * state; commits are monotonic, so applying the event is idempotent.
 */
public final class OffsetCommitProcessor implements TypedRecordProcessor<OffsetCommitRecord> {

  @Override
  public void processRecord(
      final TypedRecord<OffsetCommitRecord> command, final StateWriter stateWriter) {
    final var cmd = command.getValue();
    final var event =
        new OffsetCommitRecord()
            .setGroupId(cmd.getGroupId())
            .setPartitionId(cmd.getPartitionId())
            .setOffset(cmd.getOffset());
    stateWriter.appendFollowUpEvent(command.getKey(), CoordinatorIntent.OFFSET_COMMITTED, event);
  }
}
