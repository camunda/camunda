/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing;

import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
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
  private final BackupManager backupManager;

  public CheckpointConfirmBackupProcessor(
      final CheckpointState checkpointState, final BackupManager backupManager) {
    this.checkpointState = checkpointState;
    backupConfirmedApplier = new CheckpointBackupConfirmedApplier(checkpointState);
    this.backupManager = backupManager;
  }

  public ProcessingResult process(
      final TypedRecord<CheckpointRecord> record, final ProcessingResultBuilder resultBuilder) {
    final var checkpointRecord = record.getValue();
    final var checkpointId = checkpointRecord.getCheckpointId();
    final var latestBackupId = checkpointState.getLatestBackupId();
    final var firstLogPosition = checkpointRecord.getFirstLogPosition();
    if (latestBackupId < checkpointId) {
      LOG.debug("Confirming backup for checkpoint {}", checkpointId);
      if (latestBackupId != CheckpointState.NO_CHECKPOINT
          && firstLogPosition <= checkpointState.getLatestBackupPosition() + 1) {
        backupManager.extendRange(latestBackupId, checkpointId);
      } else {
        backupManager.startNewRange(checkpointId);
      }
      backupConfirmedApplier.apply(checkpointRecord, record.getTimestamp());
      resultBuilder.appendRecord(
          record.getKey(),
          checkpointRecord,
          new RecordMetadata()
              .recordType(RecordType.EVENT)
              .valueType(ValueType.CHECKPOINT)
              .intent(CheckpointIntent.CONFIRMED_BACKUP));
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
