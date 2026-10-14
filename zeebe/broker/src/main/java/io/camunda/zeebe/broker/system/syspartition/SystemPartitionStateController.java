/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.broker.system.partitions.AtomixRecordEntrySupplier;
import io.camunda.zeebe.broker.system.partitions.NoEntryAtSnapshotPosition;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotFileInfoProvider;
import io.camunda.zeebe.snapshots.SnapshotFilesInfo;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The system partition's counterpart of a data partition's {@code StateControllerImpl}: it owns the
 * {@link SystemKeyValueStore}, recovers it from the latest snapshot, and takes transient snapshots
 * of it. Where data partitions copy a RocksDB checkpoint into the snapshot, it flushes the store as
 * a single JSON file. When to snapshot is decided by the {@link SystemPartitionSnapshotDirector}.
 */
public final class SystemPartitionStateController {
  public static final String STATE_FILE = "system-partition.json";

  /**
   * Snapshots hold a single small JSON file, which the store checksums by reading it, so there is
   * no precomputed file information to provide.
   */
  static final SnapshotFileInfoProvider FILE_INFO_PROVIDER = path -> SnapshotFilesInfo.none();

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final FileBasedSnapshotStore snapshotStore;
  private final AtomixRecordEntrySupplier entrySupplier;
  private final SystemKeyValueStore store;

  SystemPartitionStateController(
      final FileBasedSnapshotStore snapshotStore,
      final AtomixRecordEntrySupplier entrySupplier,
      final SystemKeyValueStore store) {
    this.snapshotStore = snapshotStore;
    this.entrySupplier = entrySupplier;
    this.store = store;
  }

  /**
   * Restores the store from the latest snapshot, if any.
   *
   * @return the position of the last entry the restored store holds, or 0 if none
   */
  long recover() {
    return snapshotStore
        .getLatestSnapshot()
        .map(
            snapshot -> {
              store.restore(read(snapshot).entries());
              return snapshot.getMetadata().processedPosition();
            })
        .orElse(0L);
  }

  /** Returns the position of the last entry held by the latest snapshot, or -1 if none. */
  long latestSnapshotPosition() {
    return snapshotStore
        .getLatestSnapshot()
        .map(snapshot -> snapshot.getMetadata().processedPosition())
        .orElse(-1L);
  }

  /**
   * Takes a transient snapshot of the store, which holds every entry up to the given position. The
   * store may already hold later entries; replaying them on recovery puts the same values again.
   * Like for data partitions, a snapshot at position 0 is only taken when forced.
   */
  ActorFuture<TransientSnapshot> takeTransientSnapshot(
      final long lastAppliedPosition,
      final boolean forceSnapshot,
      final ConcurrencyControl concurrencyControl) {
    final ActorFuture<TransientSnapshot> future = concurrencyControl.createFuture();
    if (lastAppliedPosition == 0 && !forceSnapshot) {
      future.completeExceptionally(
          new IllegalArgumentException(
              "Snapshot can be taken at applied position 0 only if forced."));
      return future;
    }

    final NextSnapshotId nextSnapshotId;
    try {
      nextSnapshotId = tryFindNextSnapshotId(lastAppliedPosition);
    } catch (final NoEntryAtSnapshotPosition e) {
      future.completeExceptionally(e);
      return future;
    }

    final var state =
        new SystemPartitionState(SystemPartitionState.CURRENT_VERSION, store.entries());
    snapshotStore
        .newTransientSnapshot(
            nextSnapshotId.index,
            nextSnapshotId.term,
            lastAppliedPosition,
            lastAppliedPosition,
            forceSnapshot)
        .ifRightOrLeft(
            snapshot ->
                concurrencyControl.runOnCompletion(
                    snapshot.take(directory -> write(directory, state)),
                    (ok, error) -> {
                      if (error != null) {
                        future.completeExceptionally(error);
                      } else {
                        future.complete(snapshot);
                      }
                    }),
            future::completeExceptionally);
    return future;
  }

  /**
   * Mirrors {@code StateControllerImpl#tryFindNextSnapshotId}: the snapshot index bounds log
   * compaction, so it is the entry before the one holding the snapshot position. Without applied
   * records, the latest snapshot's index and term are reused.
   */
  private NextSnapshotId tryFindNextSnapshotId(final long lastAppliedPosition)
      throws NoEntryAtSnapshotPosition {
    final var latestSnapshot = snapshotStore.getLatestSnapshot();
    if (lastAppliedPosition == 0) {
      return latestSnapshot
          .map(snapshot -> new NextSnapshotId(snapshot.getIndex(), snapshot.getTerm()))
          .orElseGet(() -> new NextSnapshotId(0, 0));
    }

    final var logEntry = entrySupplier.getPreviousIndexedEntry(lastAppliedPosition);
    if (logEntry.isPresent()) {
      return new NextSnapshotId(logEntry.get().index(), logEntry.get().term());
    }
    if (latestSnapshot.isPresent()) {
      return new NextSnapshotId(latestSnapshot.get().getIndex(), latestSnapshot.get().getTerm());
    }
    throw new NoEntryAtSnapshotPosition(
        "Failed to take snapshot. Expected to find an indexed entry for applied position %d or a previous snapshot, but found neither."
            .formatted(lastAppliedPosition));
  }

  private static void write(final Path directory, final SystemPartitionState state) {
    try {
      Files.createDirectories(directory);
      MAPPER.writeValue(directory.resolve(STATE_FILE).toFile(), state);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static SystemPartitionState read(final PersistedSnapshot snapshot) {
    final SystemPartitionState state;
    try {
      state =
          MAPPER.readValue(
              snapshot.getPath().resolve(STATE_FILE).toFile(), SystemPartitionState.class);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    if (state.version() > SystemPartitionState.CURRENT_VERSION) {
      throw new IllegalStateException(
          "Expected system partition snapshot %s to have state version <= %d, but got %d"
              .formatted(snapshot.getId(), SystemPartitionState.CURRENT_VERSION, state.version()));
    }
    return state;
  }

  private record NextSnapshotId(long index, long term) {}
}
