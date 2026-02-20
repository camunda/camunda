/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing.state;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * State backed by the CHECKPOINTS column family. Stores full metadata for every checkpoint (all
 * types including MARKER). Keyed by checkpoint ID.
 */
public final class DbCheckpointMetadataState {

  private final DbLong checkpointIdKey;
  private final CheckpointMetadataValue metadataValue;
  private final ColumnFamily<DbLong, CheckpointMetadataValue> checkpointsColumnFamily;

  public DbCheckpointMetadataState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    checkpointIdKey = new DbLong();
    metadataValue = new CheckpointMetadataValue();
    checkpointsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CHECKPOINTS, transactionContext, checkpointIdKey, metadataValue);
  }

  /**
   * Adds a new checkpoint entry on CREATED events. Called for all checkpoint types (MARKER,
   * SCHEDULED_BACKUP, MANUAL_BACKUP).
   */
  public void addCheckpoint(
      final long checkpointId,
      final long checkpointPosition,
      final long timestamp,
      final CheckpointType type) {
    checkpointIdKey.wrapLong(checkpointId);
    metadataValue
        .setCheckpointPosition(checkpointPosition)
        .setCheckpointTimestamp(timestamp)
        .setCheckpointType(type)
        .setFirstLogPosition(-1L)
        .setNumberOfPartitions(-1)
        .setBrokerVersion("");
    checkpointsColumnFamily.insert(checkpointIdKey, metadataValue);
  }

  /**
   * Enriches an existing checkpoint entry with backup information on CONFIRMED_BACKUP events. Only
   * called for backup-type checkpoints.
   */
  public void enrichWithBackupInfo(
      final long checkpointId,
      final long firstLogPosition,
      final int numberOfPartitions,
      final String brokerVersion) {
    checkpointIdKey.wrapLong(checkpointId);
    final var existing = checkpointsColumnFamily.get(checkpointIdKey);
    if (existing != null) {
      existing
          .setFirstLogPosition(firstLogPosition)
          .setNumberOfPartitions(numberOfPartitions)
          .setBrokerVersion(brokerVersion != null ? brokerVersion : "");
      checkpointsColumnFamily.update(checkpointIdKey, existing);
    } else {
      // Entry might not exist if this is a pre-migration backup. Create it.
      metadataValue
          .setCheckpointPosition(-1L)
          .setCheckpointTimestamp(-1L)
          .setCheckpointType(CheckpointType.MANUAL_BACKUP)
          .setFirstLogPosition(firstLogPosition)
          .setNumberOfPartitions(numberOfPartitions)
          .setBrokerVersion(brokerVersion != null ? brokerVersion : "");
      checkpointsColumnFamily.insert(checkpointIdKey, metadataValue);
    }
  }

  /** Removes a checkpoint entry (used during backup deletion). */
  public void removeCheckpoint(final long checkpointId) {
    checkpointIdKey.wrapLong(checkpointId);
    checkpointsColumnFamily.deleteIfExists(checkpointIdKey);
  }

  /** Point lookup for a single checkpoint. Returns null if not found. */
  public CheckpointMetadataValue getCheckpoint(final long checkpointId) {
    checkpointIdKey.wrapLong(checkpointId);
    return checkpointsColumnFamily.get(checkpointIdKey);
  }

  /** Returns all checkpoint entries, ordered by checkpoint ID ascending. */
  public List<CheckpointEntry> getAllCheckpoints() {
    final var result = new ArrayList<CheckpointEntry>();
    checkpointsColumnFamily.forEach(
        (key, value) ->
            result.add(
                new CheckpointEntry(
                    key.getValue(),
                    value.getCheckpointPosition(),
                    value.getCheckpointTimestamp(),
                    value.getCheckpointType(),
                    value.getFirstLogPosition(),
                    value.getNumberOfPartitions(),
                    value.getBrokerVersion())));
    return result;
  }

  /** Returns true if no checkpoint entries exist (for migration detection). */
  public boolean isEmpty() {
    return checkpointsColumnFamily.isEmpty();
  }

  /**
   * Finds the predecessor backup-type checkpoint by iterating backward from {@code checkpointId -
   * 1}. Skips MARKER-type checkpoints since only backup-type checkpoints form ranges.
   *
   * @return the checkpoint ID of the nearest predecessor backup, or empty if none exists
   */
  public Optional<Long> findPredecessorBackupCheckpoint(final long checkpointId) {
    final var result = new long[] {-1L};
    final var found = new boolean[] {false};

    checkpointIdKey.wrapLong(checkpointId - 1);
    checkpointsColumnFamily.whileTrueReverse(
        checkpointIdKey,
        (key, value) -> {
          if (value.getCheckpointType().shouldCreateBackup()) {
            result[0] = key.getValue();
            found[0] = true;
            return false; // stop iteration
          }
          return true; // skip MARKERs, continue backward
        });

    return found[0] ? Optional.of(result[0]) : Optional.empty();
  }

  /**
   * Finds the successor backup-type checkpoint by iterating forward from {@code checkpointId + 1}.
   * Skips MARKER-type checkpoints since only backup-type checkpoints form ranges.
   *
   * @return the checkpoint ID of the nearest successor backup, or empty if none exists
   */
  public Optional<Long> findSuccessorBackupCheckpoint(final long checkpointId) {
    final var result = new long[] {-1L};
    final var found = new boolean[] {false};

    checkpointIdKey.wrapLong(checkpointId + 1);
    checkpointsColumnFamily.whileTrue(
        checkpointIdKey,
        (key, value) -> {
          if (value.getCheckpointType().shouldCreateBackup()) {
            result[0] = key.getValue();
            found[0] = true;
            return false; // stop iteration
          }
          return true; // skip MARKERs, continue forward
        });

    return found[0] ? Optional.of(result[0]) : Optional.empty();
  }

  /** Immutable snapshot of a checkpoint entry for external consumption. */
  public record CheckpointEntry(
      long checkpointId,
      long checkpointPosition,
      long checkpointTimestamp,
      CheckpointType checkpointType,
      long firstLogPosition,
      int numberOfPartitions,
      String brokerVersion) {}
}
