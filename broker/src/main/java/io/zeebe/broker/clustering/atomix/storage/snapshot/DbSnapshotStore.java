/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.atomix.storage.snapshot;

import io.atomix.protocols.raft.storage.snapshot.PendingSnapshot;
import io.atomix.protocols.raft.storage.snapshot.Snapshot;
import io.atomix.protocols.raft.storage.snapshot.SnapshotStore;
import io.atomix.utils.time.WallClockTimestamp;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

// TODO: does this class need to be thread-safe?
public class DbSnapshotStore implements SnapshotStore {
  // static snapshot extensions, even if they are directories, to better identify them
  static final String SNAPSHOT_EXTENSION = ".snap";

  // if thread-safe is a must, then switch to ConcurrentNavigableMap
  // a map of all available snapshots indexed by index
  private final Map<Long, DbSnapshot> snapshots;

  // the root snapshotsDirectory where all snapshots should be stored
  private final Path snapshotsDirectory;
  // the root snapshotsDirectory when pending snapshots should be stored
  private final Path pendingDirectory;
  // references the newest snapshot; may be null
  private DbSnapshot latestSnapshot;

  public DbSnapshotStore(
      final Path snapshotsDirectory,
      final Path pendingDirectory,
      final Map<Long, DbSnapshot> snapshots) {
    this.snapshotsDirectory = snapshotsDirectory;
    this.pendingDirectory = pendingDirectory;
    this.snapshots = snapshots;
  }

  public void put(final long position, final Path localSnapshot) {
    // todo: lookup entry for the given position and find its index/term
    // todo: build snapshot metadata, then build snapshot
    // todo: call {@link #put(DbSnapshot)}
  }

  public void put(final DbPendingSnapshot pendingSnapshot) {
    // todo: open snapshot, read position and use it in the file snapshot directory name
    // todo: build snapshot metadata
    // todo: move snapshot to the destination directory
    // todo: call {@link #put(DbSnapshot)} with resulting snapshot
  }

  public void put(final DbSnapshot snapshot) {
    final long index = snapshot.getMetadata().getIndex();
    snapshots.compute(index, (ignored, previous) -> takeNewestSnapshot(previous, snapshot));
  }

  public Optional<DbSnapshot> getLatestSnapshot() {
    return Optional.ofNullable(latestSnapshot);
  }

  /**
   * Returns the newest snapshot for the given index, meaning the snapshot with the given index with
   * the highest position.
   *
   * @param index index of the snapshot
   * @return a snapshot, or null if there are no known snapshots for this index
   */
  @Override
  public Snapshot getSnapshot(final long index) {
    return snapshots.get(index);
  }

  @Override
  public void close() {
    // nothing to be done
  }

  @Override
  public long getCurrentSnapshotIndex() {
    return getLatestSnapshot().map(DbSnapshot::index).orElse(0L);
  }

  @Override
  public Snapshot getCurrentSnapshot() {
    return getLatestSnapshot().orElse(null);
  }

  @Override
  public void delete() {
    // if thread-safe is a must, then the following must be atomic
    snapshots.values().forEach(DbSnapshot::delete);
    snapshots.clear();
  }

  @Override
  public PendingSnapshot newPendingSnapshot(
      final long index, final long term, final WallClockTimestamp timestamp) {
    final var directory = createPendingSnapshotDirectory(index, term, timestamp);
    return new DbPendingSnapshot(index, term, timestamp, directory, this);
  }

  @Override
  public Snapshot newSnapshot(
      final long index, final long term, final WallClockTimestamp timestamp) {
    throw new UnsupportedOperationException(
        "Deprecated operation, use PendingSnapshot to create new snapshots");
  }

  private Path createPendingSnapshotDirectory(
      final long index, final long term, final WallClockTimestamp timestamp) {
    try {
      return Files.createTempDirectory(
          pendingDirectory, String.format("%d-%d-%d", index, term, timestamp.unixTimestamp()));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private DbSnapshot takeNewestSnapshot(final DbSnapshot previous, final DbSnapshot snapshot) {
    if (previous == null || previous.getMetadata().compareTo(snapshot.getMetadata()) <= 0) {
      return snapshot;
    }

    return previous;
  }
}
