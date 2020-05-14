/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.atomix.raft.impl.zeebe.snapshot;

import io.atomix.raft.storage.snapshot.PendingSnapshot;
import io.atomix.raft.storage.snapshot.Snapshot;
import io.atomix.raft.storage.snapshot.SnapshotListener;
import io.atomix.raft.storage.snapshot.SnapshotStore;
import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.util.FileUtil;
import io.zeebe.util.ZbLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;

public final class DbSnapshotStore implements SnapshotStore {
  private static final Logger LOGGER = new ZbLogger(DbSnapshotStore.class);

  // if thread-safe is a must, then switch to ConcurrentNavigableMap
  // a map of all available snapshots indexed by index
  private final ConcurrentNavigableMap<DbSnapshotId, DbSnapshot> snapshots;
  // the root snapshotsDirectory where all snapshots should be stored
  private final Path snapshotsDirectory;
  // the root snapshotsDirectory when pending snapshots should be stored
  private final Path pendingDirectory;
  // keeps track of all snapshot modification listeners
  private final Set<SnapshotListener> listeners;
  // a pair of mutable snapshot ID for index-only lookups
  private final ReusableSnapshotId lowerBoundId;
  private final ReusableSnapshotId upperBoundId;

  public DbSnapshotStore(
      final Path snapshotsDirectory,
      final Path pendingDirectory,
      final ConcurrentNavigableMap<DbSnapshotId, DbSnapshot> snapshots) {
    this.snapshotsDirectory = snapshotsDirectory;
    this.pendingDirectory = pendingDirectory;
    this.snapshots = snapshots;

    this.lowerBoundId = new ReusableSnapshotId(WallClockTimestamp.from(0));
    this.upperBoundId = new ReusableSnapshotId(WallClockTimestamp.from(Long.MAX_VALUE));
    this.listeners = new CopyOnWriteArraySet<>();
  }

  /**
   * Returns the newest snapshot for the given index, meaning the snapshot with the given index with
   * the highest timestamp.
   *
   * @param index index of the snapshot
   * @return a snapshot, or null if there are no known snapshots for this index
   */
  @Override
  public Snapshot getSnapshot(final long index) {
    // it's possible (though unlikely) to have more than one snapshot per index, so we fallback to
    // the one with the highest timestamp
    final var indexBoundedSet =
        snapshots.subMap(lowerBoundId.setIndex(index), false, upperBoundId.setIndex(index), false);
    if (indexBoundedSet.isEmpty()) {
      return null;
    }

    return indexBoundedSet.lastEntry().getValue();
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
    // currently only called by Atomix when permanently leaving a cluster - it should be safe here
    // to not update the metrics, as they will simply disappear as time moves on. Once we have a
    // single store/replication mechanism, we can consider updating the metrics here
    snapshots.clear();

    try {
      FileUtil.deleteFolder(snapshotsDirectory);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    try {
      FileUtil.deleteFolder(pendingDirectory);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public PendingSnapshot newPendingSnapshot(
      final long index, final long term, final WallClockTimestamp timestamp) {
    final var directory = buildPendingSnapshotDirectory(index, term, timestamp);
    return new DbPendingSnapshot(index, term, timestamp, directory, this);
  }

  @Override
  public Snapshot newSnapshot(
      final long index, final long term, final WallClockTimestamp timestamp, final Path directory) {
    return put(directory, new DbSnapshotMetadata(index, term, timestamp));
  }

  @Override
  public Snapshot newSnapshot(
      final long index, final long term, final WallClockTimestamp timestamp) {
    throw new UnsupportedOperationException(
        "Deprecated operation, use PendingSnapshot to create new snapshots");
  }

  @Override
  public void purgeSnapshots(final Snapshot snapshot) {
    if (!(snapshot instanceof DbSnapshot)) {
      throw new IllegalArgumentException(
          String.format(
              "Expected purge request with known DbSnapshot, but receive '%s'",
              snapshot.getClass()));
    }

    final DbSnapshot dbSnapshot = (DbSnapshot) snapshot;
    snapshots.headMap(dbSnapshot.getMetadata(), false).values().forEach(this::remove);
  }

  @Override
  public void purgePendingSnapshots() throws IOException {
    try (final var files = Files.list(pendingDirectory)) {
      files.filter(Files::isDirectory).forEach(this::purgePendingSnapshot);
    }
  }

  @Override
  public Path getPath() {
    return snapshotsDirectory;
  }

  @Override
  public Collection<? extends Snapshot> getSnapshots() {
    return snapshots.values();
  }

  @Override
  public void addListener(final SnapshotListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(final SnapshotListener listener) {
    listeners.remove(listener);
  }

  private void purgePendingSnapshot(final Path pendingSnapshot) {
    try {
      FileUtil.deleteFolder(pendingSnapshot);
      LOGGER.debug("Delete not completed (orphaned) snapshot {}", pendingSnapshot);
    } catch (final IOException e) {
      LOGGER.error("Failed to delete not completed (orphaned) snapshot {}", pendingSnapshot);
    }
  }

  private DbSnapshot put(final DbSnapshot snapshot) {
    // caveat: if the metadata is the same but the location is different, this will do nothing
    final var previous = snapshots.put(snapshot.getMetadata(), snapshot);
    if (previous == null) {
      listeners.forEach(listener -> listener.onNewSnapshot(snapshot, this));
    }

    LOGGER.debug("Committed new snapshot {}", snapshot);
    return snapshot;
  }

  private DbSnapshot put(final Path directory, final DbSnapshotMetadata metadata) {
    if (snapshots.containsKey(metadata)) {
      LOGGER.debug("Snapshot {} already exists", metadata);
      return snapshots.get(metadata);
    }

    final var destination = buildSnapshotDirectory(metadata);
    try {
      tryAtomicDirectoryMove(directory, destination);
    } catch (final FileAlreadyExistsException e) {
      LOGGER.debug(
          "Expected to move snapshot from {} to {}, but it already exists",
          directory,
          destination,
          e);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    return put(new DbSnapshot(destination, metadata));
  }

  private void tryAtomicDirectoryMove(final Path directory, final Path destination)
      throws IOException {
    try {
      Files.move(directory, destination, StandardCopyOption.ATOMIC_MOVE);
    } catch (final AtomicMoveNotSupportedException e) {
      Files.move(directory, destination);
    }
  }

  private Optional<DbSnapshot> getLatestSnapshot() {
    return Optional.ofNullable(snapshots.lastEntry()).map(Entry::getValue);
  }

  private void remove(final DbSnapshot snapshot) {
    LOGGER.debug("Deleting snapshot {}", snapshot);
    snapshot.delete();
    snapshots.remove(snapshot.getMetadata());
    listeners.forEach(l -> l.onSnapshotDeletion(snapshot, this));
    LOGGER.trace("Snapshots count: {}", snapshots.size());
  }

  private Path buildPendingSnapshotDirectory(
      final long index, final long term, final WallClockTimestamp timestamp) {
    final var metadata = new DbSnapshotMetadata(index, term, timestamp);
    return pendingDirectory.resolve(metadata.getFileName());
  }

  private Path buildSnapshotDirectory(final DbSnapshotMetadata metadata) {
    return snapshotsDirectory.resolve(metadata.getFileName());
  }

  private static final class ReusableSnapshotId implements DbSnapshotId {
    private final WallClockTimestamp timestamp;
    private long index;

    private ReusableSnapshotId(final WallClockTimestamp position) {
      this.timestamp = position;
    }

    @Override
    public long getIndex() {
      return index;
    }

    @Override
    public WallClockTimestamp getTimestamp() {
      return timestamp;
    }

    private ReusableSnapshotId setIndex(final long index) {
      this.index = index;
      return this;
    }
  }
}
