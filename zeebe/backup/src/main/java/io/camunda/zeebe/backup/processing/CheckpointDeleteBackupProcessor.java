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

/**
 * Processes DELETE_BACKUP commands. When a delete command is received, it writes a DELETING_BACKUP
 * event, clears the checkpoint/backup state if needed, and starts the async deletion. The backup
 * manager writes the CONFIRM_DELETION command when the deletion completes.
 */
public class CheckpointDeleteBackupProcessor {

  private final BackupManager backupManager;
  private final CheckpointDeletingBackupApplier deletingBackupApplier;

  public CheckpointDeleteBackupProcessor(
      final BackupManager backupManager, final CheckpointState checkpointState) {
    this.backupManager = backupManager;
    deletingBackupApplier = new CheckpointDeletingBackupApplier(checkpointState);
  }

  public ProcessingResult process(
      final TypedRecord<CheckpointRecord> record, final ProcessingResultBuilder resultBuilder) {
    final var checkpointRecord = record.getValue();
    final long backupId = checkpointRecord.getCheckpointId();

    // Start async deletion - the backup manager will write CONFIRM_DELETION when done
    backupManager.deleteBackup(backupId);

    final var followupRecord =
        new CheckpointRecord()
            .setCheckpointId(backupId)
            .setCheckpointPosition(record.getPosition())
            .setCheckpointType(checkpointRecord.getCheckpointType());

    // Apply state changes using the applier
    deletingBackupApplier.apply(followupRecord);

    return resultBuilder
        .appendRecord(
            record.getKey(),
            followupRecord,
            new RecordMetadata()
                .recordType(RecordType.EVENT)
                .valueType(ValueType.CHECKPOINT)
                .intent(CheckpointIntent.DELETING_BACKUP))
        .build();
  }
}
