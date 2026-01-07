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
 * Processes CONFIRM_DELETION commands. When a confirm deletion command is received, it writes a
 * CONFIRMED_BACKUP_DELETION event. State clearing happens on the DELETING_BACKUP event to ensure we
 * always update the state even if the backup store fails to delete.
 */
public class CheckpointConfirmDeletionProcessor {

  private final CheckpointBackupDeletionConfirmedApplier backupDeletionConfirmedApplier;

  public CheckpointConfirmDeletionProcessor() {
    backupDeletionConfirmedApplier = new CheckpointBackupDeletionConfirmedApplier();
  }

  public ProcessingResult process(
      final TypedRecord<CheckpointRecord> record, final ProcessingResultBuilder resultBuilder) {
    final var checkpointRecord = record.getValue();

    // Apply state changes using the applier (currently no-op)
    backupDeletionConfirmedApplier.apply(checkpointRecord);

    resultBuilder.appendRecord(
        record.getKey(),
        checkpointRecord,
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.CHECKPOINT)
            .intent(CheckpointIntent.CONFIRMED_BACKUP_DELETION));

    return resultBuilder.build();
  }
}
