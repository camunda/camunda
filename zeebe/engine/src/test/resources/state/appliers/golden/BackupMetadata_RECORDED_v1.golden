/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableBackupMetadataState;
import io.camunda.zeebe.protocol.impl.record.value.backupmetadata.BackupMetadataRecord;
import io.camunda.zeebe.protocol.record.intent.BackupMetadataIntent;

/**
 * Deterministic state applier for {@code BACKUP_METADATA} events. Applied on every replica of the
 * system partition by the engine's event-applier pipeline.
 *
 * <p>One instance is used per intent variant — the variant is decided by which {@link
 * BackupMetadataIntent} the applier was registered for in {@link
 * EventAppliers#registerEventAppliers}.
 *
 * <ul>
 *   <li>{@link BackupMetadataIntent#RECORDED} / {@link BackupMetadataIntent#MARKED_FAILED} →
 *       insert/replace the row keyed by {@code (checkpointId, partitionId)}.
 *   <li>{@link BackupMetadataIntent#DELETED} → remove the row.
 * </ul>
 */
final class BackupMetadataStateApplier
    implements TypedEventApplier<BackupMetadataIntent, BackupMetadataRecord> {

  private final MutableBackupMetadataState state;
  private final BackupMetadataIntent intent;

  BackupMetadataStateApplier(
      final MutableBackupMetadataState state, final BackupMetadataIntent intent) {
    this.state = state;
    this.intent = intent;
  }

  @Override
  public void applyState(final long key, final BackupMetadataRecord value) {
    switch (intent) {
      case RECORDED, MARKED_FAILED -> state.put(value);
      case DELETED -> state.delete(value.getCheckpointId(), value.getPartitionId());
      default -> {
        // Commands (RECORD/MARK_FAILED/DELETE) and any unexpected event variants are no-ops.
      }
    }
  }
}
