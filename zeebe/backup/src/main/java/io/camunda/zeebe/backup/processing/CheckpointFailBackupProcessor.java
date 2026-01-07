/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing;

import io.camunda.zeebe.backup.api.BackupManager;
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

/**
 * Processes FAIL_BACKUP commands. When a fail backup command is received, it marks the backup as
 * failed in the store and writes a FAILED_BACKUP event.
 */
public class CheckpointFailBackupProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(CheckpointFailBackupProcessor.class);

  private final BackupManager backupManager;
  private final CheckpointBackupFailedApplier backupFailedApplier;

  public CheckpointFailBackupProcessor(final BackupManager backupManager) {
    this.backupManager = backupManager;
    backupFailedApplier = new CheckpointBackupFailedApplier();
  }

  public ProcessingResult process(
      final TypedRecord<CheckpointRecord> record, final ProcessingResultBuilder resultBuilder) {
    final var checkpointRecord = record.getValue();
    final long checkpointId = checkpointRecord.getCheckpointId();

    LOG.debug("Recording backup failure for checkpoint {}", checkpointId);

    // Mark the backup as failed in the store
    backupManager.markBackupAsFailed(checkpointId, "Backup failed");

    // Apply state changes using the applier (currently no-op)
    backupFailedApplier.apply(checkpointRecord);

    resultBuilder.appendRecord(
        record.getKey(),
        checkpointRecord,
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.CHECKPOINT)
            .intent(CheckpointIntent.FAILED_BACKUP));

    return resultBuilder.build();
  }
}
