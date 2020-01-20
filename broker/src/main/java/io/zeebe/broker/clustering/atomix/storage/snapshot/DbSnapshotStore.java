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
import io.atomix.protocols.raft.storage.snapshot.SnapshotListener;
import io.atomix.protocols.raft.storage.snapshot.SnapshotStore;
import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.broker.logstreams.state.StatePositionSupplier;
import io.zeebe.util.FileUtil;
import io.zeebe.util.ZbLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

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
  // used to fetch the position for snapshots replicated by Atomix
  private final StatePositionSupplier positionSupplier;
  // a pair of mutable snapshot ID for index-only lookups
  private final ReusableSnapshotId lowerBoundId;
  private final ReusableSnapshotId upperBoundId;

  DbSnapshotStore(
      final Path snapshotsDirectory,
      final Path pendingDirectory,
      final ConcurrentNavigableMap<DbSnapshotId, DbSnapshot> snapshots) {
    this.snapshotsDirectory = snapshotsDirectory;
    this.pendingDirectory = pendingDirectory;
    this.snapshots = snapshots;

    this.positionSupplier = new StatePositionSupplier(-1, NOPLogger.NOP_LOGGER);
    this.lowerBoundId = new ReusableSnapshotId(Long.MIN_VALUE);
    this.upperBoundId = new ReusableSnapshotId(Long.MAX_VALUE);
    this.listeners = new CopyOnWriteArraySet<>();
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
    // compute a map of all snapshots with index equal to the given index, and pick the one with the
    // highest position
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
    final var metadata = DbSnapshotMetadata.ofPath(directory);
    if (metadata.isPresent()) {
      return put(directory, metadata.get());
    }

    // as a fallback, read the position from the DB; Atomix does not propagate position information
    // when replicating, so this is necessary
    final var position = positionSupplier.getLowestPosition(directory);
    return put(directory, new DbSnapshotMetadata(index, term, timestamp, position));
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

    try {
      cleanUpTemporarySnapshots(dbSnapshot.getMetadata());
    } catch (final IOException e) {
      LOGGER.error("Failed to remove orphaned temporary snapshot older than {}", dbSnapshot, e);
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
      Files.move(directory, destination);
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

  private void cleanUpTemporarySnapshots(final DbSnapshotId cutoffId) throws IOException {
    LOGGER.debug("Search for orphaned snapshots below oldest valid snapshot {}", cutoffId);

    try (final var files = Files.newDirectoryStream(pendingDirectory)) {
      for (final var file : files) {
        final var name = file.getFileName().toString();
        final var parts = name.split("-", 3);
        final var index = Long.parseLong(parts[0]);
        if (cutoffId.getIndex() < index) {
          FileUtil.deleteFolder(file);
          LOGGER.debug("Delete not completed (orphaned) snapshot {}", file);
        }
      }
    }
  }

  private Path buildPendingSnapshotDirectory(
      final long index, final long term, final WallClockTimestamp timestamp) {
    final var name = String.format("%d-%d-%d", index, term, timestamp.unixTimestamp());
    return pendingDirectory.resolve(name);
  }

  private Path buildSnapshotDirectory(final DbSnapshotMetadata metadata) {
    return snapshotsDirectory.resolve(
        String.format(
            "%d-%d-%d-%d",
            metadata.getIndex(),
            metadata.getTerm(),
            metadata.getTimestamp().unixTimestamp(),
            metadata.getPosition()));
  }

  private static final class ReusableSnapshotId implements DbSnapshotId {
    private final long position;
    private long index;

    private ReusableSnapshotId(final long position) {
      this.position = position;
    }

    @Override
    public long getIndex() {
      return index;
    }

    @Override
    public long getPosition() {
      return position;
    }

    private ReusableSnapshotId setIndex(final long index) {
      this.index = index;
      return this;
    }
  }
}
