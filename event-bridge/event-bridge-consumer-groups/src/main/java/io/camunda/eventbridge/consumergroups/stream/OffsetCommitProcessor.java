/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.stream;

import io.camunda.eventbridge.stream.StreamRecordProcessor;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Command processor for consumer offset commits on the coordinator partition. Following the
 * engine's command-processor / event-applier split, it does <em>not</em> mutate state directly: on
 * a commit command it appends an {@code OFFSET_COMMITTED} follow-up event via the {@link
 * io.camunda.eventbridge.stream.StateWriter}, which both writes the event and applies it through
 * the registered {@link OffsetCommittedApplier}. The same applier runs on replay, so every replica
 * converges to identical offsets and a new leader resumes without loss. Commits are monotonic, so
 * applying an event is idempotent.
 */
public final class OffsetCommitProcessor extends StreamRecordProcessor {

  public OffsetCommitProcessor(final OffsetState offsetState) {
    super(EventBridgeRecordValues.OFFSET_VALUE_TYPE);
    appliers()
        .register(CoordinatorIntent.OFFSET_COMMITTED, new OffsetCommittedApplier(offsetState));
  }

  @Override
  protected void processCommand(final TypedRecord command) {
    final var cmd = (OffsetCommitRecord) command.getValue();

    // Command handling produces the follow-up event; the applier (not the command) mutates state,
    // monotonically, so the leader applies exactly what a follower will replay.
    final var event =
        new OffsetCommitRecord()
            .setGroupId(cmd.getGroupId())
            .setPartitionId(cmd.getPartitionId())
            .setOffset(cmd.getOffset());

    stateWriter().appendFollowUpEvent(command.getKey(), CoordinatorIntent.OFFSET_COMMITTED, event);
  }
}
