/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition.processors;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.BackupMetadataState;
import io.camunda.zeebe.protocol.impl.record.value.backupmetadata.BackupMetadataRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.BackupMetadataIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

/**
 * Processor for {@link BackupMetadataIntent} commands ({@code RECORD}, {@code MARK_FAILED}, {@code
 * DELETE}). Validates per-row status transitions and emits the corresponding {@code *ED} event.
 *
 * <p>Status transitions:
 *
 * <ul>
 *   <li>From absent: any of {@code PENDING}, {@code COMPLETED}, {@code FAILED} is accepted (initial
 *       insert).
 *   <li>{@code PENDING} → {@code COMPLETED} (RECORD) or {@code FAILED} (MARK_FAILED).
 *   <li>{@code COMPLETED} is terminal except for delete.
 *   <li>{@code FAILED} is terminal except for delete.
 * </ul>
 *
 * <p>{@code DELETE} commands are accepted unconditionally (idempotent for absent rows).
 */
public final class BackupMetadataRecordProcessor
    implements TypedRecordProcessor<BackupMetadataRecord> {

  static final String STATUS_PENDING = "PENDING";
  static final String STATUS_COMPLETED = "COMPLETED";
  static final String STATUS_FAILED = "FAILED";

  private final BackupMetadataState state;
  private final Writers writers;
  private final KeyGenerator keys;

  public BackupMetadataRecordProcessor(
      final BackupMetadataState state, final Writers writers, final KeyGenerator keys) {
    this.state = state;
    this.writers = writers;
    this.keys = keys;
  }

  @Override
  public void processRecord(final TypedRecord<BackupMetadataRecord> command) {
    final BackupMetadataRecord cmd = command.getValue();
    final BackupMetadataIntent intent = (BackupMetadataIntent) command.getIntent();
    switch (intent) {
      case RECORD -> handleRecord(command, cmd);
      case MARK_FAILED -> handleMarkFailed(command, cmd);
      case DELETE -> handleDelete(cmd);
      default ->
          writers
              .rejection()
              .appendRejection(
                  command,
                  RejectionType.INVALID_ARGUMENT,
                  "Unsupported BackupMetadata command: " + intent);
    }
  }

  private void handleRecord(
      final TypedRecord<BackupMetadataRecord> command, final BackupMetadataRecord cmd) {
    final BackupMetadataRecord existing = state.get(cmd.getCheckpointId(), cmd.getPartitionId());
    if (existing != null && isTerminal(existing.getStatus())) {
      writers
          .rejection()
          .appendRejection(
              command,
              RejectionType.INVALID_STATE,
              "Backup row "
                  + cmd.getCheckpointId()
                  + "/"
                  + cmd.getPartitionId()
                  + " is in terminal status "
                  + existing.getStatus()
                  + "; cannot RECORD over it");
      return;
    }
    writers.state().appendFollowUpEvent(keys.nextKey(), BackupMetadataIntent.RECORDED, cmd);
  }

  private void handleMarkFailed(
      final TypedRecord<BackupMetadataRecord> command, final BackupMetadataRecord cmd) {
    final BackupMetadataRecord existing = state.get(cmd.getCheckpointId(), cmd.getPartitionId());
    if (existing != null && STATUS_COMPLETED.equals(existing.getStatus())) {
      writers
          .rejection()
          .appendRejection(
              command,
              RejectionType.INVALID_STATE,
              "Cannot MARK_FAILED a COMPLETED backup row "
                  + cmd.getCheckpointId()
                  + "/"
                  + cmd.getPartitionId());
      return;
    }
    // Force the FAILED status on the event regardless of what the caller passed (defensive).
    final BackupMetadataRecord event =
        new BackupMetadataRecord()
            .setCheckpointId(cmd.getCheckpointId())
            .setPartitionId(cmd.getPartitionId())
            .setStatus(STATUS_FAILED)
            .setFailureReason(cmd.getFailureReason());
    writers.state().appendFollowUpEvent(keys.nextKey(), BackupMetadataIntent.MARKED_FAILED, event);
  }

  private void handleDelete(final BackupMetadataRecord cmd) {
    // DELETE is idempotent: even if the row is already gone, emit a DELETED event so callers
    // (orchestrator, mirror) observe the operation. The applier is a no-op for missing rows.
    final BackupMetadataRecord event =
        new BackupMetadataRecord()
            .setCheckpointId(cmd.getCheckpointId())
            .setPartitionId(cmd.getPartitionId());
    writers.state().appendFollowUpEvent(keys.nextKey(), BackupMetadataIntent.DELETED, event);
  }

  private static boolean isTerminal(final String status) {
    return STATUS_COMPLETED.equals(status) || STATUS_FAILED.equals(status);
  }
}
