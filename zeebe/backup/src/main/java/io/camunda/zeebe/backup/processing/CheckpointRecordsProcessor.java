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
import io.camunda.zeebe.backup.processing.state.DbBackupRangeState;
import io.camunda.zeebe.backup.processing.state.DbCheckpointMetadataState;
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
  private final boolean trackBackupMetadata;
  private CheckpointCreateProcessor checkpointCreateProcessor;
  private CheckpointConfirmBackupProcessor checkpointConfirmBackupProcessor;
  private CheckpointDeleteBackupProcessor checkpointDeleteBackupProcessor;
  private CheckpointClearStateProcessor checkpointClearStateProcessor;
  private CheckpointCreatedEventApplier checkpointCreatedEventApplier;
  private CheckpointBackupConfirmedApplier checkpointBackupConfirmedApplier;
  private CheckpointBackupDeletedApplier checkpointBackupDeletedApplier;
  private CheckpointStateClearedApplier checkpointStateClearedApplier;

  //  Can be accessed concurrently by other threads to add new listeners. Hence we have to use a
  // thread safe collection
  private final Set<CheckpointListener> checkpointListeners = new CopyOnWriteArraySet<>();
  private final CheckpointMetrics metrics;
  private DbCheckpointState checkpointState;
  private DbCheckpointMetadataState checkpointMetadataState;
  private DbBackupRangeState backupRangeState;
  private ProcessingScheduleService executor;
  private ScalingStatusSupplier scalingInProgressSupplier;
  private PartitionCountSupplier partitionCountSupplier;

  public CheckpointRecordsProcessor(
      final BackupManager backupManager,
      final MeterRegistry registry,
      final boolean trackBackupMetadata) {
    this.backupManager = backupManager;
    this.trackBackupMetadata = trackBackupMetadata;
    metrics = new CheckpointMetrics(registry);
  }

  public void setScalingInProgressSupplier(final ScalingStatusSupplier scalingInProgressSupplier) {
    this.scalingInProgressSupplier = scalingInProgressSupplier;
  }

  /**
   * Sets the supplier that provides the current partition count from routing information. This must
   * be called before init() is called.
   *
   * @param partitionCountSupplier supplier that returns current partition count
   */
  public void setPartitionCountSupplier(final PartitionCountSupplier partitionCountSupplier) {
    this.partitionCountSupplier = partitionCountSupplier;
  }

  @Override
  public void init(final RecordProcessorContext recordProcessorContext) {
    executor = recordProcessorContext.getScheduleService();
    checkpointState =
        new DbCheckpointState(
            recordProcessorContext.getZeebeDb(), recordProcessorContext.getTransactionContext());

    checkpointMetadataState =
        new DbCheckpointMetadataState(
            recordProcessorContext.getZeebeDb(), recordProcessorContext.getTransactionContext());
    backupRangeState =
        new DbBackupRangeState(
            recordProcessorContext.getZeebeDb(), recordProcessorContext.getTransactionContext());

    if (scalingInProgressSupplier == null) {
      throw new IllegalStateException("Scaling in progress supplier is not initialized.");
    }
    if (partitionCountSupplier == null) {
      throw new IllegalStateException("Partition count supplier is not initialized.");
    }
    checkpointCreatedEventApplier =
        new CheckpointCreatedEventApplier(
            checkpointState, checkpointMetadataState, checkpointListeners, trackBackupMetadata);
    checkpointBackupConfirmedApplier =
        new CheckpointBackupConfirmedApplier(
            checkpointState, checkpointMetadataState, backupRangeState, trackBackupMetadata);
    checkpointBackupDeletedApplier =
        new CheckpointBackupDeletedApplier(
            checkpointMetadataState, backupRangeState, checkpointState);
    checkpointStateClearedApplier =
        new CheckpointStateClearedApplier(
            checkpointState, checkpointMetadataState, backupRangeState);

    checkpointCreateProcessor =
        new CheckpointCreateProcessor(
            checkpointState,
            backupManager,
            scalingInProgressSupplier,
            partitionCountSupplier,
            metrics,
            checkpointCreatedEventApplier);

    checkpointConfirmBackupProcessor =
        new CheckpointConfirmBackupProcessor(
            checkpointState,
            checkpointMetadataState,
            backupRangeState,
            backupManager,
            checkpointBackupConfirmedApplier);

    checkpointDeleteBackupProcessor =
        new CheckpointDeleteBackupProcessor(
            checkpointMetadataState, backupRangeState, checkpointState, backupManager);

    checkpointClearStateProcessor =
        new CheckpointClearStateProcessor(
            checkpointState, checkpointMetadataState, backupRangeState, backupManager);

    final long checkpointId = checkpointState.getLatestCheckpointId();
    final var checkpointType = checkpointState.getLatestCheckpointType();
    if (checkpointId != CheckpointState.NO_CHECKPOINT) {
      checkpointListeners.forEach(
          listener -> listener.onNewCheckpointCreated(checkpointId, checkpointType));
      metrics.setCheckpointId(checkpointId, checkpointState.getLatestCheckpointPosition());
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
    if (!record.getIntent().isEvent()) {
      throw new IllegalArgumentException(
          "Expected to replay event but got non-event with intent: %s"
              .formatted(record.getIntent()));
    }

    final CheckpointIntent intent = (CheckpointIntent) record.getIntent();
    switch (intent) {
      case CREATED ->
          checkpointCreatedEventApplier.apply(
              (CheckpointRecord) record.getValue(), record.getTimestamp());
      case CONFIRMED_BACKUP ->
          checkpointBackupConfirmedApplier.apply(
              (CheckpointRecord) record.getValue(), record.getTimestamp());
      case DELETED_BACKUP ->
          checkpointBackupDeletedApplier.apply((CheckpointRecord) record.getValue());
      case STATE_CLEARED -> checkpointStateClearedApplier.apply();
      case IGNORED -> {}
      default ->
          throw new IllegalStateException(
              "Unknown checkpoint intent: %s".formatted(record.getIntent()));
    }
  }

  @Override
  public ProcessingResult process(
      final TypedRecord record, final ProcessingResultBuilder resultBuilder) {
    if (record.getValueType() != ValueType.CHECKPOINT) {
      throw new IllegalArgumentException(
          "Unknown value type %s for record %s".formatted(record.getValueType(), record));
    }

    if (!(record.getIntent() instanceof final CheckpointIntent checkpointIntent)) {
      throw new IllegalArgumentException(
          "Unknown checkpoint intent %s".formatted(record.getIntent()));
    }

    return switch (checkpointIntent) {
      case CheckpointIntent.CREATE -> checkpointCreateProcessor.process(record, resultBuilder);
      case CheckpointIntent.CONFIRM_BACKUP ->
          checkpointConfirmBackupProcessor.process(record, resultBuilder);
      case CheckpointIntent.DELETE_BACKUP ->
          checkpointDeleteBackupProcessor.process(record, resultBuilder);
      case CheckpointIntent.CLEAR_STATE ->
          checkpointClearStateProcessor.process(record, resultBuilder);
      default ->
          throw new IllegalArgumentException(
              "Unknown checkpoint intent %s".formatted(record.getIntent()));
    };
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
            final var checkpointId = checkpointState.getLatestCheckpointId();
            final var checkpointType = checkpointState.getLatestCheckpointType();
            if (checkpointId != CheckpointState.NO_CHECKPOINT) {
              checkpointListener.onNewCheckpointCreated(checkpointId, checkpointType);
            }
          });
    }
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    // After a leader change, the new leader will not continue taking the backup initiated by
    // previous leader. So mark them as failed, so that the users do not wait forever for it to be
    // completed.
    backupManager.failInProgressBackup(checkpointState.getLatestCheckpointId());
  }
}
