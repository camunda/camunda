/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing.state;

import io.camunda.zeebe.backup.api.Checkpoint;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.agrona.collections.MutableLong;

/**
 * State backed by the CHECKPOINTS column family. Stores full metadata for every created markers and
 * confirmed backups. Keyed by checkpoint ID.
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

  /** Adds a new checkpoint entry when marker checkpoints are created. */
  public void addMarkerCheckpoint(
      final long checkpointId, final long checkpointPosition, final long timestamp) {
    checkpointIdKey.wrapLong(checkpointId);
    metadataValue.reset();
    metadataValue
        .setCheckpointPosition(checkpointPosition)
        .setCheckpointTimestamp(timestamp)
        .setCheckpointType(CheckpointType.MARKER);
    checkpointsColumnFamily.insert(checkpointIdKey, metadataValue);
  }

  /** Adds a new checkpoint entry when a backup is created. */
  public void addBackupCheckpoint(
      final long checkpointId,
      final long checkpointPosition,
      final long checkpointTimestamp,
      final CheckpointType checkpointType,
      final long firstLogPosition) {
    checkpointIdKey.wrapLong(checkpointId);
    metadataValue.reset();
    metadataValue
        .setCheckpointPosition(checkpointPosition)
        .setCheckpointTimestamp(checkpointTimestamp)
        .setCheckpointType(checkpointType)
        .setFirstLogPosition(firstLogPosition);
    checkpointsColumnFamily.insert(checkpointIdKey, metadataValue);
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
  public List<Checkpoint> getAllCheckpoints() {
    final var result = new ArrayList<Checkpoint>();
    checkpointsColumnFamily.forEach(
        (key, value) ->
            result.add(
                new Checkpoint(
                    value.getCheckpointType(),
                    key.getValue(),
                    value.getCheckpointTimestamp(),
                    value.getCheckpointPosition(),
                    value.getFirstLogPosition())));
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
    final var result = new MutableLong(CheckpointState.NO_CHECKPOINT);

    checkpointIdKey.wrapLong(checkpointId - 1);
    checkpointsColumnFamily.whileTrueReverse(
        checkpointIdKey,
        (key, value) -> {
          if (value.getCheckpointType().shouldCreateBackup()) {
            result.set(key.getValue());
            return false; // stop iteration
          }
          return true; // skip MARKERs, continue backward
        });

    return result.get() != CheckpointState.NO_CHECKPOINT
        ? Optional.of(result.get())
        : Optional.empty();
  }

  /**
   * Finds the successor backup-type checkpoint by iterating forward from {@code checkpointId + 1}.
   * Skips MARKER-type checkpoints since only backup-type checkpoints form ranges.
   *
   * @return the checkpoint ID of the nearest successor backup, or empty if none exists
   */
  public Optional<Long> findSuccessorBackupCheckpoint(final long checkpointId) {
    final var result = new MutableLong(CheckpointState.NO_CHECKPOINT);

    checkpointIdKey.wrapLong(checkpointId + 1);
    checkpointsColumnFamily.whileTrue(
        checkpointIdKey,
        (key, value) -> {
          if (value.getCheckpointType().shouldCreateBackup()) {
            result.set(key.getValue());
            return false; // stop iteration
          }
          return true; // skip MARKERs, continue forward
        });

    return result.get() != CheckpointState.NO_CHECKPOINT
        ? Optional.of(result.get())
        : Optional.empty();
  }

  public void removeCheckpointsUntil(final long firstLogPosition) {
    checkpointsColumnFamily.whileTrue(
        (checkpointId, checkpointMetadataValue) -> {
          if (checkpointMetadataValue.getFirstLogPosition() < firstLogPosition) {
            checkpointsColumnFamily.deleteExisting(checkpointId);
            return true;
          } else {
            return false;
          }
        });
  }

  /** Removes all checkpoint entries. Used during state reset when switching backup stores. */
  public void clearAll() {
    checkpointsColumnFamily.forEachKey(checkpointsColumnFamily::deleteExisting);
  }
}
