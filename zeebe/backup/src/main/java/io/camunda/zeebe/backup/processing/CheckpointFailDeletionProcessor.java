/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing;

import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Processes FAIL_DELETION commands. When a fail deletion command is received, it writes a
 * FAILED_BACKUP_DELETION event. This is used when the backup store fails to delete a backup.
 */
public class CheckpointFailDeletionProcessor {

  private final CheckpointBackupDeletionFailedApplier backupDeletionFailedApplier;

  public CheckpointFailDeletionProcessor() {
    backupDeletionFailedApplier = new CheckpointBackupDeletionFailedApplier();
  }

  public ProcessingResult process(
      final TypedRecord<CheckpointRecord> record, final ProcessingResultBuilder resultBuilder) {
    final var checkpointRecord = record.getValue();
    final long backupId = checkpointRecord.getCheckpointId();

    // Apply state changes using the applier (currently no-op)
    backupDeletionFailedApplier.apply(checkpointRecord);

    return resultBuilder
        .appendRecord(
            record.getKey(),
            checkpointRecord,
            new RecordMetadata()
                .recordType(RecordType.EVENT)
                .valueType(ValueType.CHECKPOINT)
                .intent(CheckpointIntent.FAILED_BACKUP_DELETION))
        .build();
  }
}
