/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing;

import io.camunda.zeebe.backup.management.BackupMetadataSyncer;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.backup.processing.state.DbBackupRangeState;
import io.camunda.zeebe.backup.processing.state.DbCheckpointMetadataState;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckpointConfirmBackupProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(CheckpointConfirmBackupProcessor.class);
  private final CheckpointState checkpointState;
  private final CheckpointBackupConfirmedApplier backupConfirmedApplier;
  private final DbCheckpointMetadataState checkpointMetadataState;
  private final DbBackupRangeState backupRangeState;
  private final BackupMetadataSyncer syncer;
  private final int partitionId;

  /**
   * @param checkpointState the checkpoint state
   * @param checkpointMetadataState the checkpoint metadata CF state
   * @param backupRangeState the backup ranges CF state
   * @param syncer the metadata syncer, or null if no backup store is configured
   * @param partitionId the partition ID
   */
  public CheckpointConfirmBackupProcessor(
      final CheckpointState checkpointState,
      final DbCheckpointMetadataState checkpointMetadataState,
      final DbBackupRangeState backupRangeState,
      final BackupMetadataSyncer syncer,
      final int partitionId) {
    this.checkpointState = checkpointState;
    this.checkpointMetadataState = checkpointMetadataState;
    this.backupRangeState = backupRangeState;
    this.syncer = syncer;
    this.partitionId = partitionId;
    backupConfirmedApplier =
        new CheckpointBackupConfirmedApplier(
            checkpointState, checkpointMetadataState, backupRangeState);
  }

  public ProcessingResult process(
      final TypedRecord<CheckpointRecord> record, final ProcessingResultBuilder resultBuilder) {
    final var checkpointRecord = record.getValue();
    final var checkpointId = checkpointRecord.getCheckpointId();
    final var latestBackupId = checkpointState.getLatestBackupId();
    if (latestBackupId < checkpointId) {
      LOG.debug("Confirming backup for checkpoint {}", checkpointId);
      backupConfirmedApplier.apply(
          checkpointRecord, record.getTimestamp(), record.getBrokerVersion());
      resultBuilder.appendRecord(
          record.getKey(),
          checkpointRecord,
          new RecordMetadata()
              .recordType(RecordType.EVENT)
              .valueType(ValueType.CHECKPOINT)
              .intent(CheckpointIntent.CONFIRMED_BACKUP));

      // Schedule JSON metadata sync as a post-commit task (fire-and-forget)
      if (syncer != null) {
        resultBuilder.appendPostCommitTask(
            () -> {
              syncer.sync(partitionId, checkpointMetadataState, backupRangeState);
              return true;
            });
      }
    } else {
      LOG.debug(
          "Ignoring backup for checkpoint {} as it is older than the latest backup {}",
          checkpointId,
          latestBackupId);
      resultBuilder.appendRecord(
          record.getKey(),
          checkpointRecord,
          new RecordMetadata()
              .recordType(RecordType.COMMAND_REJECTION)
              .valueType(ValueType.CHECKPOINT)
              .intent(record.getIntent()));
    }

    return resultBuilder.build();
  }
}
