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
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.metrics.CheckpointMetrics;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Set;

public final class CheckpointCreateProcessor {
  private final CheckpointState checkpointState;
  private final BackupManager backupManager;
  private final CheckpointMetrics metrics;
  private final ScalingStatusSupplier scalingStatusSupplier;
  private final CheckpointCreatedEventApplier checkpointCreatedApplier;
  private final PartitionCountSupplier partitionCountSupplier;

  public CheckpointCreateProcessor(
      final CheckpointState checkpointState,
      final BackupManager backupManager,
      final Set<CheckpointListener> listeners,
      final ScalingStatusSupplier scalingStatusSupplier,
      final PartitionCountSupplier partitionCountSupplier,
      final CheckpointMetrics metrics) {
    this.checkpointState = checkpointState;
    this.backupManager = backupManager;
    this.scalingStatusSupplier = scalingStatusSupplier;
    this.metrics = metrics;
    this.partitionCountSupplier = partitionCountSupplier;
    checkpointCreatedApplier =
        new CheckpointCreatedEventApplier(checkpointState, listeners, metrics);
  }

  public ProcessingResult process(
      final TypedRecord<CheckpointRecord> record, final ProcessingResultBuilder resultBuilder) {

    final var checkpointRecord = record.getValue();
    final long checkpointId = checkpointRecord.getCheckpointId();

    if (checkpointState.getLatestCheckpointId() < checkpointId) {
      // Only process checkpoint if it is newer
      return processNewCheckpoint(record, resultBuilder);
    } else {
      // A checkpoint already exists. Ignore the command.
      return processExistingCheckpoint(record, resultBuilder);
    }
  }

  private ProcessingResult processNewCheckpoint(
      final TypedRecord<CheckpointRecord> record, final ProcessingResultBuilder resultBuilder) {

    final boolean scalingInProgress = scalingStatusSupplier.isScalingInProgress();
    final var checkpointRecord = record.getValue();
    // Get current partition count from routing information if available
    final int currentPartitionCount = partitionCountSupplier.getCurrentPartitionCount();
    final long checkpointId = checkpointRecord.getCheckpointId();
    final var descriptor = BackupDescriptorImpl.from(record, currentPartitionCount);

    // Create backup (either normal or failed based on scaling state)
    if (scalingInProgress) {
      // We want to mark the backup as failed for observability
      backupManager.createFailedBackup(
          checkpointId, descriptor, "Cannot create checkpoint while scaling is in progress");
    } else if (checkpointRecord.getCheckpointType().shouldCreateBackup()) {
      backupManager.takeBackup(checkpointId, descriptor);
    }

    // Create follow-up record
    final var followupRecord =
        new CheckpointRecord()
            .setCheckpointId(checkpointRecord.getCheckpointId())
            .setCheckpointPosition(record.getPosition())
            .setCheckpointType(checkpointRecord.getCheckpointType());

    // Checkpoint should be created even if we don't take a backup for checkpoint-consistency
    appendCheckpointCreatedEvent(record, resultBuilder, followupRecord);

    checkpointCreatedApplier.apply(followupRecord, record.getTimestamp());

    // Handle client response based on scaling state
    if (scalingInProgress) {
      addRejectionResponse(record, resultBuilder);
    } else {
      addSuccessResponse(record, resultBuilder, followupRecord);
    }

    return resultBuilder.build();
  }

  private ProcessingResult processExistingCheckpoint(
      final TypedRecord<CheckpointRecord> record, final ProcessingResultBuilder resultBuilder) {

    metrics.ignored();
    // Use the latest checkpoint info in the response for client information
    final var latestCheckpointRecord =
        new CheckpointRecord()
            .setCheckpointId(checkpointState.getLatestCheckpointId())
            .setCheckpointPosition(checkpointState.getLatestCheckpointPosition())
            .setCheckpointType(checkpointState.getLatestCheckpointType());

    return createFollowUpAndResponse(
        record, CheckpointIntent.IGNORED, latestCheckpointRecord, resultBuilder);
  }

  private void appendCheckpointCreatedEvent(
      final TypedRecord<CheckpointRecord> record,
      final ProcessingResultBuilder resultBuilder,
      final CheckpointRecord followupRecord) {

    resultBuilder.appendRecord(
        record.getKey(),
        followupRecord,
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .intent(CheckpointIntent.CREATED)
            .rejectionType(RejectionType.NULL_VAL)
            .rejectionReason("")
            .operationReference(record.getOperationReference()));
  }

  private void addRejectionResponse(
      final TypedRecord<CheckpointRecord> record, final ProcessingResultBuilder resultBuilder) {

    if (record.hasRequestMetadata()) {
      resultBuilder.withResponse(
          RecordType.COMMAND_REJECTION,
          record.getKey(),
          CheckpointIntent.CREATE,
          record.getValue(),
          ValueType.CHECKPOINT,
          RejectionType.INVALID_STATE,
          "Cannot create checkpoint while scaling is in progress",
          record.getRequestId(),
          record.getRequestStreamId());
    }
  }

  private void addSuccessResponse(
      final TypedRecord<CheckpointRecord> record,
      final ProcessingResultBuilder resultBuilder,
      final CheckpointRecord followupRecord) {

    if (record.hasRequestMetadata()) {
      resultBuilder.withResponse(
          RecordType.EVENT,
          record.getKey(),
          CheckpointIntent.CREATED,
          followupRecord,
          ValueType.CHECKPOINT,
          RejectionType.NULL_VAL,
          "",
          record.getRequestId(),
          record.getRequestStreamId());
    }
  }

  private ProcessingResult createFollowUpAndResponse(
      final TypedRecord<CheckpointRecord> command,
      final CheckpointIntent resultIntent,
      final CheckpointRecord checkpointRecord,
      final ProcessingResultBuilder resultBuilder) {
    resultBuilder.appendRecord(
        command.getKey(),
        checkpointRecord,
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .intent(resultIntent)
            .rejectionType(RejectionType.NULL_VAL)
            .rejectionReason("")
            .operationReference(command.getOperationReference()));

    if (command.hasRequestMetadata()) {
      resultBuilder.withResponse(
          RecordType.EVENT,
          command.getKey(),
          resultIntent,
          checkpointRecord,
          ValueType.CHECKPOINT,
          RejectionType.NULL_VAL,
          "",
          command.getRequestId(),
          command.getRequestStreamId());
    }
    return resultBuilder.build();
  }
}
