/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.processing;

import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.api.CheckpointListener;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.engine.api.ProcessingResult;
import io.camunda.zeebe.engine.api.ProcessingResultBuilder;
import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import java.util.Set;

public class CheckpointCreateProcessor {
  private final CheckpointState checkpointState;
  private final BackupManager backupManager;

  private final Set<CheckpointListener> listeners;

  public CheckpointCreateProcessor(
      final CheckpointState checkpointState,
      final BackupManager backupManager,
      final Set<CheckpointListener> listeners) {
    this.checkpointState = checkpointState;
    this.backupManager = backupManager;
    this.listeners = listeners;
  }

  public ProcessingResult process(
      final TypedRecord<CheckpointRecord> record, final ProcessingResultBuilder resultBuilder) {

    final var checkpointRecord = record.getValue();
    final long checkpointId = checkpointRecord.getCheckpointId();
    if (checkpointState.getCheckpointId() < checkpointId) {
      // Only take a checkpoint if it is newer
      final var checkpointPosition = record.getPosition();
      backupManager.takeBackup(checkpointId, checkpointPosition);
      checkpointState.setCheckpointInfo(checkpointId, checkpointPosition);

      // Notify listeners immediately
      listeners.forEach(l -> l.onNewCheckpointCreated(checkpointId));

      final var followupRecord =
          new CheckpointRecord()
              .setCheckpointId(checkpointId)
              .setCheckpointPosition(checkpointPosition);
      return createFollowUpAndResponse(
          record, CheckpointIntent.CREATED, followupRecord, resultBuilder);
    } else {
      // A checkpoint already exists. Hence ignore the command. Note:- this is not an error, so not
      // considered as a "rejection"
      return createFollowUpAndResponse(
          record, CheckpointIntent.IGNORED, checkpointRecord, resultBuilder);
    }
  }

  private ProcessingResult createFollowUpAndResponse(
      final TypedRecord<CheckpointRecord> command,
      final CheckpointIntent resultIntent,
      final CheckpointRecord checkpointRecord,
      final ProcessingResultBuilder resultBuilder) {
    resultBuilder.appendRecord(
        command.getKey(), RecordType.EVENT, resultIntent, null, null, checkpointRecord);

    if (command.hasRequestMetadata()) {
      resultBuilder.withResponse(
          RecordType.EVENT,
          command.getKey(),
          resultIntent,
          checkpointRecord,
          ValueType.CHECKPOINT,
          RejectionType.NULL_VAL,
          null,
          command.getRequestId(),
          command.getRequestStreamId());
    }
    return resultBuilder.build();
  }
}
