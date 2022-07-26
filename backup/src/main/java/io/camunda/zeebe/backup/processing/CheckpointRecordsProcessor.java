/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.processing;

import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.processing.state.DbCheckpointState;
import io.camunda.zeebe.engine.api.ProcessingResult;
import io.camunda.zeebe.engine.api.ProcessingResultBuilder;
import io.camunda.zeebe.engine.api.RecordProcessor;
import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Process and replays records related to Checkpoint. */
public final class CheckpointRecordsProcessor implements RecordProcessor<Context> {

  private static final Logger LOG = LoggerFactory.getLogger(CheckpointRecordsProcessor.class);

  private final BackupManager backupManager;
  private CheckpointCreateProcessor checkpointCreateProcessor;
  private CheckpointCreatedEventApplier checkpointCreatedEventApplier;

  public CheckpointRecordsProcessor(final BackupManager backupManager) {
    this.backupManager = backupManager;
  }

  @Override
  public void init(final Context recordProcessorContext) {
    final var checkpointState =
        new DbCheckpointState(
            recordProcessorContext.zeebeDb(), recordProcessorContext.transactionContext());

    checkpointCreateProcessor =
        new CheckpointCreateProcessor(checkpointState, backupManager, Set.of());
    checkpointCreatedEventApplier = new CheckpointCreatedEventApplier(checkpointState);
  }

  @Override
  public void replay(final TypedRecord record) {
    if (record.getValueType() != ValueType.CHECKPOINT) {
      // Should never reach here. StreamProcessor must choose the right processor always.
      throw new IllegalArgumentException("Unknown record");
    }
    final CheckpointIntent intent = (CheckpointIntent) record.getIntent();
    if (intent == CheckpointIntent.CREATED) {
      checkpointCreatedEventApplier.apply((CheckpointRecord) record.getValue());
    }
    // Don't apply intents CREATE and IGNORED
  }

  @Override
  public ProcessingResult process(
      final TypedRecord record, final ProcessingResultBuilder resultBuilder) {
    if (record.getValueType() == ValueType.CHECKPOINT
        && record.getIntent() == CheckpointIntent.CREATE) {
      return checkpointCreateProcessor.process(record, resultBuilder);
    }
    // Should never reach here. StreamProcessor must choose the right processor always.
    throw new IllegalArgumentException("Unknown record");
  }

  @Override
  public ProcessingResult onProcessingError(
      final Throwable processingException,
      final TypedRecord record,
      final ProcessingResultBuilder processingResultBuilder) {
    LOG.error("Could not process checkpoint record {}.", record.getValue(), processingException);
    // There is no correct way to handle this error. If processing checkpoint create failed, and we
    // continue with processing, it can violate the consistency guarantees provided by the
    // checkpointing algorithm. The only way to guarantee correctness is preventing StreamProcessor
    // from making progress.
    throw new RuntimeException(processingException);
  }
}
