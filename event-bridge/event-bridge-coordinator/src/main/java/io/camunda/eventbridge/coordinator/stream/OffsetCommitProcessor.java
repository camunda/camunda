/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.coordinator.stream;

import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.stream.api.EmptyProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.api.RecordProcessorContext;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Processes consumer offset commits on the coordinator partition.
 *
 * <ul>
 *   <li><b>Leader</b> ({@link #process}): applies the commit to {@link OffsetState} and appends an
 *       {@code OFFSET_COMMITTED} event so the change is replicated and replayable.
 *   <li><b>Follower / recovery</b> ({@link #replay}): re-applies the committed event to its own
 *       {@link OffsetState}, yielding identical state on every replica → clean failover.
 * </ul>
 *
 * <p>Dispatch is by {@code RecordType} (the platform calls {@code process} for COMMAND records and
 * {@code replay} for EVENT records), so the processor never branches on intent. Commits are
 * monotonic, so re-applying an event is idempotent.
 */
public final class OffsetCommitProcessor implements RecordProcessor {

  private final OffsetState offsetState;

  public OffsetCommitProcessor(final OffsetState offsetState) {
    this.offsetState = offsetState;
  }

  @Override
  public void init(final RecordProcessorContext recordProcessorContext) {
    // State is injected; nothing to initialize.
  }

  @Override
  public boolean accepts(final ValueType valueType) {
    return valueType == EventBridgeRecordValues.OFFSET_VALUE_TYPE;
  }

  @Override
  public void replay(final TypedRecord record) {
    final var event = (OffsetCommitRecord) record.getValue();
    offsetState.commit(event.getGroupId(), event.getPartitionId(), event.getOffset());
  }

  @Override
  public ProcessingResult process(
      final TypedRecord record, final ProcessingResultBuilder processingResultBuilder) {
    final var command = (OffsetCommitRecord) record.getValue();
    final long committed =
        offsetState.commit(command.getGroupId(), command.getPartitionId(), command.getOffset());

    final var event =
        new OffsetCommitRecord()
            .setGroupId(command.getGroupId())
            .setPartitionId(command.getPartitionId())
            .setOffset(committed);
    final var metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(EventBridgeRecordValues.OFFSET_VALUE_TYPE)
            .intent(CoordinatorIntent.OFFSET_COMMITTED);

    processingResultBuilder.appendRecord(record.getKey(), event, metadata);
    return processingResultBuilder.build();
  }

  @Override
  public ProcessingResult onProcessingError(
      final Throwable processingException,
      final TypedRecord record,
      final ProcessingResultBuilder processingResultBuilder) {
    // Offset commits are idempotent and non-critical: drop on error rather than block the stream.
    return EmptyProcessingResult.INSTANCE;
  }
}
