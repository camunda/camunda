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
  private CheckpointConfirmBackupProcessor checkpointConfirmBackupProcessor;
  private CheckpointFailBackupProcessor checkpointFailBackupProcessor;
  private CheckpointDeleteBackupProcessor checkpointDeleteBackupProcessor;
  private CheckpointConfirmDeletionProcessor checkpointConfirmDeletionProcessor;
  private CheckpointFailDeletionProcessor checkpointFailDeletionProcessor;
  private CheckpointCreatedEventApplier checkpointCreatedEventApplier;
  private CheckpointBackupConfirmedApplier checkpointBackupConfirmedApplier;
  private CheckpointBackupFailedApplier checkpointBackupFailedApplier;
  private CheckpointDeletingBackupApplier checkpointDeletingBackupApplier;
  private CheckpointBackupDeletionConfirmedApplier checkpointBackupDeletionConfirmedApplier;
  private CheckpointBackupDeletionFailedApplier checkpointBackupDeletionFailedApplier;

  //  Can be accessed concurrently by other threads to add new listeners. Hence we have to use a
  // thread safe collection
  private final Set<CheckpointListener> checkpointListeners = new CopyOnWriteArraySet<>();
  private final CheckpointMetrics metrics;
  private DbCheckpointState checkpointState;
  private ProcessingScheduleService executor;
  private ScalingStatusSupplier scalingInProgressSupplier;
  private PartitionCountSupplier partitionCountSupplier;

  public CheckpointRecordsProcessor(
      final BackupManager backupManager, final int partitionId, final MeterRegistry registry) {
    this.backupManager = backupManager;
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

    if (scalingInProgressSupplier == null) {
      throw new IllegalStateException("Scaling in progress supplier is not initialized.");
    }
    if (partitionCountSupplier == null) {
      throw new IllegalStateException("Partition count supplier is not initialized.");
    }

    checkpointCreateProcessor =
        new CheckpointCreateProcessor(
            checkpointState,
            backupManager,
            checkpointListeners,
            scalingInProgressSupplier,
            partitionCountSupplier,
            metrics);

    checkpointConfirmBackupProcessor = new CheckpointConfirmBackupProcessor(checkpointState);
    checkpointFailBackupProcessor = new CheckpointFailBackupProcessor(backupManager);
    checkpointDeleteBackupProcessor =
        new CheckpointDeleteBackupProcessor(backupManager, checkpointState);
    checkpointConfirmDeletionProcessor = new CheckpointConfirmDeletionProcessor();
    checkpointFailDeletionProcessor = new CheckpointFailDeletionProcessor();
    checkpointCreatedEventApplier =
        new CheckpointCreatedEventApplier(checkpointState, checkpointListeners, metrics);
    checkpointBackupConfirmedApplier = new CheckpointBackupConfirmedApplier(checkpointState);
    checkpointBackupFailedApplier = new CheckpointBackupFailedApplier();
    checkpointDeletingBackupApplier = new CheckpointDeletingBackupApplier(checkpointState);
    checkpointBackupDeletionConfirmedApplier = new CheckpointBackupDeletionConfirmedApplier();
    checkpointBackupDeletionFailedApplier = new CheckpointBackupDeletionFailedApplier();

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
    final CheckpointIntent intent = (CheckpointIntent) record.getIntent();
    switch (intent) {
      case CREATED ->
          checkpointCreatedEventApplier.apply(
              (CheckpointRecord) record.getValue(), record.getTimestamp());
      case CONFIRMED_BACKUP ->
          checkpointBackupConfirmedApplier.apply(
              (CheckpointRecord) record.getValue(), record.getTimestamp());
      case FAILED_BACKUP ->
          checkpointBackupFailedApplier.apply((CheckpointRecord) record.getValue());
      case DELETING_BACKUP ->
          checkpointDeletingBackupApplier.apply((CheckpointRecord) record.getValue());
      case CONFIRMED_BACKUP_DELETION ->
          checkpointBackupDeletionConfirmedApplier.apply((CheckpointRecord) record.getValue());
      case FAILED_BACKUP_DELETION ->
          checkpointBackupDeletionFailedApplier.apply((CheckpointRecord) record.getValue());
      default -> {
        // Don't apply intents CREATE, IGNORED, DELETE_BACKUP, CONFIRM_DELETION, FAIL_DELETION,
        // CONFIRM_BACKUP (commands), FAIL_BACKUP (command)
      }
    }
  }

  @Override
  public ProcessingResult process(
      final TypedRecord record, final ProcessingResultBuilder resultBuilder) {
    if (record.getValueType() == ValueType.CHECKPOINT
        && record.getIntent() == CheckpointIntent.CREATE) {
      return checkpointCreateProcessor.process(record, resultBuilder);
    }

    if (record.getValueType() == ValueType.CHECKPOINT
        && record.getIntent() == CheckpointIntent.CONFIRM_BACKUP) {
      return checkpointConfirmBackupProcessor.process(record, resultBuilder);
    }

    if (record.getValueType() == ValueType.CHECKPOINT
        && record.getIntent() == CheckpointIntent.FAIL_BACKUP) {
      return checkpointFailBackupProcessor.process(record, resultBuilder);
    }

    if (record.getValueType() == ValueType.CHECKPOINT
        && record.getIntent() == CheckpointIntent.DELETE_BACKUP) {
      return checkpointDeleteBackupProcessor.process(record, resultBuilder);
    }

    if (record.getValueType() == ValueType.CHECKPOINT
        && record.getIntent() == CheckpointIntent.CONFIRM_DELETION) {
      return checkpointConfirmDeletionProcessor.process(record, resultBuilder);
    }

    if (record.getValueType() == ValueType.CHECKPOINT
        && record.getIntent() == CheckpointIntent.FAIL_DELETION) {
      return checkpointFailDeletionProcessor.process(record, resultBuilder);
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
