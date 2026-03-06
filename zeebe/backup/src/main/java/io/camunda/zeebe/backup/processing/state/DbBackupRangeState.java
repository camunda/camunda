/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing.state;

import io.camunda.zeebe.backup.api.BackupRange;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.ArrayList;
import java.util.Optional;
import java.util.SequencedCollection;
import org.agrona.collections.MutableLong;

/**
 * State backed by the BACKUP_RANGES column family. Maintains pre-computed contiguous backup ranges
 * so they can be queried without scanning all checkpoints.
 *
 * <p>Each entry maps a range start checkpoint ID (key) to the range end checkpoint ID (value). A
 * range [start, end] means that all backup-type checkpoints from start to end form a contiguous
 * sequence (each checkpoint's log starts at or before the previous checkpoint's position + 1).
 */
public final class DbBackupRangeState {

  private final DbLong rangeStartKey;
  private final DbLong rangeEndValue;
  private final ColumnFamily<DbLong, DbLong> rangesColumnFamily;

  public DbBackupRangeState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    rangeStartKey = new DbLong();
    rangeEndValue = new DbLong();
    rangesColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.BACKUP_RANGES, transactionContext, rangeStartKey, rangeEndValue);
  }

  /**
   * Starts a new range containing a single checkpoint. Inserts (checkpointId, checkpointId).
   *
   * @param checkpointId the checkpoint that starts (and currently ends) the new range
   */
  public void startNewRange(final long checkpointId) {
    rangeStartKey.wrapLong(checkpointId);
    rangeEndValue.wrapLong(checkpointId);
    rangesColumnFamily.insert(rangeStartKey, rangeEndValue);
  }

  /**
   * Finds the range that contains the given checkpoint ID. Seeks backward from checkpointId, the
   * first entry found has the largest start <= checkpointId, then verifies that end >=
   * checkpointId.
   *
   * @param checkpointId the checkpoint ID to look up
   * @return the range containing the checkpoint, or empty if not in any range
   */
  public Optional<BackupRange> findRangeContaining(final long checkpointId) {
    final var start = new MutableLong(CheckpointState.NO_CHECKPOINT);
    final var end = new MutableLong(CheckpointState.NO_CHECKPOINT);

    rangeStartKey.wrapLong(checkpointId);
    rangesColumnFamily.whileTrueReverse(
        rangeStartKey,
        (key, value) -> {
          // First entry with start <= checkpointId
          final var startValue = key.getValue();
          final var endValue = value.getValue();
          if (endValue >= checkpointId) {
            start.set(startValue);
            end.set(endValue);
          }
          return false; // only need the first entry
        });

    return start.get() != CheckpointState.NO_CHECKPOINT
        ? Optional.of(new BackupRange(start.get(), end.get()))
        : Optional.empty();
  }

  /** Returns all ranges, ordered by start checkpoint ID ascending. */
  public SequencedCollection<BackupRange> getAllRanges() {
    final var result = new ArrayList<BackupRange>();
    rangesColumnFamily.forEach(
        (key, value) -> result.add(new BackupRange(key.getValue(), value.getValue())));
    return result;
  }

  public Optional<BackupRange> getFirstRange() {
    final var startValue = new MutableLong(CheckpointState.NO_CHECKPOINT);
    final var endValue = new MutableLong(CheckpointState.NO_CHECKPOINT);
    rangesColumnFamily.whileTrue(
        (start, end) -> {
          startValue.set(start.getValue());
          endValue.set(end.getValue());
          return false;
        });
    return startValue.get() != CheckpointState.NO_CHECKPOINT
        ? Optional.of(new BackupRange(startValue.get(), endValue.get()))
        : Optional.empty();
  }

  /**
   * Deletes a range entry. Used when the only checkpoint in a range is deleted.
   *
   * @param startCheckpointId the start key of the range to delete
   */
  public void deleteRange(final long startCheckpointId) {
    rangeStartKey.wrapLong(startCheckpointId);
    rangesColumnFamily.deleteExisting(rangeStartKey);
  }

  /**
   * Updates the end of a range. Used when extending a range to include a new checkpoint.
   *
   * @param startCheckpointId the start key of the range to extend
   * @param newEndCheckpointId the new end checkpoint ID
   */
  public void updateRangeEnd(final long startCheckpointId, final long newEndCheckpointId) {
    rangeStartKey.wrapLong(startCheckpointId);
    rangeEndValue.wrapLong(newEndCheckpointId);
    rangesColumnFamily.update(rangeStartKey, rangeEndValue);
  }

  /**
   * Updates the start of a range. Deletes the old entry and inserts a new one with the updated
   * start. Used when deleting the first checkpoint in a range.
   *
   * @param oldStart the current start key
   * @param newStart the new start key
   */
  public void updateRangeStart(final long oldStart, final long newStart) {
    rangeStartKey.wrapLong(oldStart);
    final var endCheckpointId = rangesColumnFamily.get(rangeStartKey).getValue();
    rangesColumnFamily.deleteExisting(rangeStartKey);

    rangeStartKey.wrapLong(newStart);
    rangeEndValue.wrapLong(endCheckpointId);
    rangesColumnFamily.insert(rangeStartKey, rangeEndValue);
  }

  /**
   * Splits a range into two sub-ranges around a deleted checkpoint. Removes the old range and
   * inserts two new ones: [oldStart, predecessorId] and [successorId, oldEnd].
   *
   * @param oldStart the original range start
   * @param oldEnd the original range end
   * @param predecessorId the last checkpoint before the deleted one (becomes end of left sub-range)
   * @param successorId the first checkpoint after the deleted one (becomes start of right
   *     sub-range)
   */
  public void splitRange(
      final long oldStart, final long oldEnd, final long predecessorId, final long successorId) {
    // Delete the original range
    rangeStartKey.wrapLong(oldStart);
    rangesColumnFamily.deleteExisting(rangeStartKey);

    // Insert left sub-range: [oldStart, predecessorId]
    rangeStartKey.wrapLong(oldStart);
    rangeEndValue.wrapLong(predecessorId);
    rangesColumnFamily.insert(rangeStartKey, rangeEndValue);

    // Insert right sub-range: [successorId, oldEnd]
    rangeStartKey.wrapLong(successorId);
    rangeEndValue.wrapLong(oldEnd);
    rangesColumnFamily.insert(rangeStartKey, rangeEndValue);
  }

  /** Removes all range entries. Used during state reset when switching backup stores. */
  public void clearAll() {
    rangesColumnFamily.forEachKey(rangesColumnFamily::deleteExisting);
  }
}
