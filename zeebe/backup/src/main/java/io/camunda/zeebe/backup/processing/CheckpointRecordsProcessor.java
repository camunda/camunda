/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing;

import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.api.CheckpointListener;
import io.camunda.zeebe.backup.metrics.CheckpointMetrics;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.backup.processing.state.DbCheckpointState;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.api.RecordProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Process and replays records related to Checkpoint. */
public final class CheckpointRecordsProcessor
    implements RecordProcessor, StreamProcessorLifecycleAware {

  private static final Logger LOG = LoggerFactory.getLogger(CheckpointRecordsProcessor.class);

  private final BackupManager backupManager;
  private CheckpointCreateProcessor checkpointCreateProcessor;
  private CheckpointCreatedEventApplier checkpointCreatedEventApplier;

  //  Can be accessed concurrently by other threads to add new listeners. Hence we have to use a
  // thread safe collection
  private final Set<CheckpointListener> checkpointListeners = new CopyOnWriteArraySet<>();
  private final CheckpointMetrics metrics;
  private DbCheckpointState checkpointState;
  private ProcessingScheduleService executor;

  public CheckpointRecordsProcessor(
      final BackupManager backupManager, final int partitionId, final MeterRegistry registry) {
    this.backupManager = backupManager;
    metrics = new CheckpointMetrics(registry);
  }

  @Override
  public void init(final RecordProcessorContext recordProcessorContext) {
    executor = recordProcessorContext.getScheduleService();
    checkpointState =
        new DbCheckpointState(
            recordProcessorContext.getZeebeDb(), recordProcessorContext.getTransactionContext());

    checkpointCreateProcessor =
        new CheckpointCreateProcessor(checkpointState, backupManager, checkpointListeners, metrics);
    checkpointCreatedEventApplier =
        new CheckpointCreatedEventApplier(checkpointState, checkpointListeners, metrics);

    final long checkpointId = checkpointState.getCheckpointId();
    if (checkpointId != CheckpointState.NO_CHECKPOINT) {
      checkpointListeners.forEach(listener -> listener.onNewCheckpointCreated(checkpointId));
      metrics.setCheckpointId(checkpointId, checkpointState.getCheckpointPosition());
    }

    recordProcessorContext.addLifecycleListeners(List.of(this));
  }

  @Override
  public boolean accepts(final ValueType valueType) {
    return valueType == ValueType.CHECKPOINT;
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

  /**
   * Registers a listener. If a checkpoint exists, then the listener will be immediately notified
   * with the current checkpointId.
   *
   * @param checkpointListener
   */
  public void addCheckpointListener(final CheckpointListener checkpointListener) {
    checkpointListeners.add(checkpointListener);
    // Can read the checkpoint only after init() is called.
    if (executor != null) {
      executor.runDelayed(
          Duration.ZERO,
          () -> {
            final var checkpointId = checkpointState.getCheckpointId();
            if (checkpointId != CheckpointState.NO_CHECKPOINT) {
              checkpointListener.onNewCheckpointCreated(checkpointState.getCheckpointId());
            }
          });
    }
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    // After a leader change, the new leader will not continue taking the backup initiated by
    // previous leader. So mark them as failed, so that the users do not wait forever for it to be
    // completed.
    backupManager.failInProgressBackup(checkpointState.getCheckpointId());
  }
}
