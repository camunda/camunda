/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.broker.system.partitions.AtomixRecordEntrySupplier;
import io.camunda.zeebe.broker.system.partitions.NoEntryAtSnapshotPosition;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * Finds the log entry that the next snapshot is taken at. The log is compacted up to that entry, so
 * it must not be past a record that isn't processed, exported or backed up yet.
 */
@NullMarked
final class SnapshotIdFinder {

  private static final Logger LOG = Loggers.SNAPSHOT_LOGGER;

  private final AtomixRecordEntrySupplier entrySupplier;
  private final ConstructableSnapshotStore snapshotStore;

  SnapshotIdFinder(
      final AtomixRecordEntrySupplier entrySupplier,
      final ConstructableSnapshotStore snapshotStore) {
    this.entrySupplier = requireNonNull(entrySupplier);
    this.snapshotStore = requireNonNull(snapshotStore);
  }

  /**
   * @param exportedPosition the lowest position the exporters have exported, or -1 if unknown
   * @param backupPosition the highest position in a backup, or -1 if unknown
   * @throws NoEntryAtSnapshotPosition if there's neither a log entry before the snapshot position
   *     nor an earlier snapshot to take the index and term from
   */
  NextSnapshotId find(
      final long processedPosition, final long exportedPosition, final long backupPosition)
      throws NoEntryAtSnapshotPosition {
    if (exportedPosition == -1 || backupPosition == -1 || processedPosition == 0) {
      final var latestSnapshot = snapshotStore.getLatestSnapshot();
      if (latestSnapshot.isPresent()) {
        // re-use index and term from the latest snapshot to ensure that the records from there are
        // not compacted until they get exported and backed up.
        final var persistedSnapshot = latestSnapshot.get();
        return new NextSnapshotId(
            persistedSnapshot.getIndex(), persistedSnapshot.getTerm(), processedPosition, 0);
      }

      return new NextSnapshotId(0, 0, processedPosition, 0);
    }

    final var snapshotPosition =
        Math.min(Math.min(exportedPosition, backupPosition), processedPosition);
    final var logEntry = entrySupplier.getPreviousIndexedEntry(snapshotPosition);

    if (logEntry.isPresent()) {
      return new NextSnapshotId(
          logEntry.get().index(), logEntry.get().term(), processedPosition, exportedPosition);
    }

    // No log entry for snapshot position - try to use the index and term of the last snapshot to
    // take new one
    final var latestSnapshot = snapshotStore.getLatestSnapshot();
    if (latestSnapshot.isPresent()) {
      LOG.warn(
          "No log entry for next snapshot position {}, using index and term from previous snapshot",
          snapshotPosition);
      return new NextSnapshotId(
          latestSnapshot.get().getIndex(),
          latestSnapshot.get().getTerm(),
          processedPosition,
          exportedPosition);
    }

    throw new NoEntryAtSnapshotPosition(
        String.format(
            "Failed to take snapshot. Expected to find an indexed entry for determined snapshot position %d (processedPosition = %d, exportedPosition=%d) or previous snapshot, but found neither.",
            snapshotPosition, processedPosition, exportedPosition));
  }

  record NextSnapshotId(long index, long term, long processedPosition, long exportedPosition) {}
}
