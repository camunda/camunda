/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.atomix.storage.snapshot;

import io.atomix.protocols.raft.storage.snapshot.SnapshotListener;
import io.atomix.protocols.raft.storage.snapshot.SnapshotStore;
import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.broker.clustering.atomix.storage.AtomixRecordEntrySupplier;
import io.zeebe.logstreams.state.Snapshot;
import io.zeebe.logstreams.state.SnapshotDeletionListener;
import io.zeebe.logstreams.state.SnapshotMetrics;
import io.zeebe.logstreams.state.SnapshotStorage;
import io.zeebe.util.ZbLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Stream;
import org.slf4j.Logger;

public final class AtomixSnapshotStorage implements SnapshotStorage, SnapshotListener {
  private static final Logger LOGGER = new ZbLogger(AtomixSnapshotStorage.class);

  private final Path runtimeDirectory;
  private final AtomixRecordEntrySupplier entrySupplier;
  private final SnapshotStore store;
  private final int maxSnapshotCount;
  private final Set<SnapshotDeletionListener> deletionListeners;
  private final SnapshotMetrics metrics;

  public AtomixSnapshotStorage(
      final Path runtimeDirectory,
      final SnapshotStore store,
      final AtomixRecordEntrySupplier entrySupplier,
      final int maxSnapshotCount,
      final SnapshotMetrics metrics) {
    this.runtimeDirectory = runtimeDirectory;
    this.entrySupplier = entrySupplier;
    this.store = store;
    this.maxSnapshotCount = maxSnapshotCount;
    this.metrics = metrics;

    this.deletionListeners = new CopyOnWriteArraySet<>();
    this.store.addListener(this);

    observeExistingSnapshots();
  }

  @Override
  public Snapshot getPendingSnapshotFor(final long snapshotPosition) {
    final var optionalIndexed = entrySupplier.getIndexedEntry(snapshotPosition);

    if (optionalIndexed.isPresent()) {
      final var indexed = optionalIndexed.get();
      final var pending =
          store.newPendingSnapshot(
              indexed.index(),
              indexed.entry().term(),
              WallClockTimestamp.from(System.currentTimeMillis()));
      final var pendingPath = pending.getPath();
      final var realPath =
          pendingPath.resolveSibling(
              String.format("%s-%d", pendingPath.getFileName(), snapshotPosition));
      return new SnapshotImpl(snapshotPosition, realPath);
    } else {
      LOGGER.debug(
          "No previous entry found for position {}, cannot take snapshot", snapshotPosition);
    }

    return null;
  }

  @Override
  public Path getPendingDirectoryFor(final String id) {
    final var optionalMeta = DbSnapshotMetadata.ofFileName(id);
    if (optionalMeta.isPresent()) {
      final var metadata = optionalMeta.get();
      return getPendingDirectoryFor(
          metadata.getIndex(), metadata.getTerm(), metadata.getTimestamp(), metadata.getPosition());
    }

    return null;
  }

  @Override
  public boolean commitSnapshot(final Path snapshotPath) {
    // in the case of DbSnapshot instances, we expect the path to always contain all the metadata
    try (final var created = store.newSnapshot(-1, -1, null, snapshotPath)) {
      return created != null;
    }
  }

  @Override
  public Optional<Snapshot> getLatestSnapshot() {
    return Optional.ofNullable(store.getCurrentSnapshot())
        .flatMap(snapshot -> toSnapshot(snapshot.getPath()));
  }

  @Override
  public Stream<Snapshot> getSnapshots() {
    return store.getSnapshots().stream()
        .map(snapshot -> toSnapshot(snapshot.getPath()))
        .filter(Optional::isPresent)
        .map(Optional::get);
  }

  @Override
  public Path getRuntimeDirectory() {
    return runtimeDirectory;
  }

  @Override
  public boolean exists(final String id) {
    return Files.exists(store.getPath().resolve(id));
  }

  @Override
  public void close() {
    deletionListeners.clear();
    store.removeListener(this);
    entrySupplier.close();
  }

  @Override
  public void addDeletionListener(final SnapshotDeletionListener listener) {
    deletionListeners.add(listener);
  }

  @Override
  public void removeDeletionListener(final SnapshotDeletionListener listener) {
    deletionListeners.remove(listener);
  }

  @Override
  public SnapshotMetrics getMetrics() {
    return metrics;
  }

  @Override
  public void onNewSnapshot(
      final io.atomix.protocols.raft.storage.snapshot.Snapshot snapshot,
      final SnapshotStore store) {
    final var snapshots = store.getSnapshots();
    metrics.incrementSnapshotCount();
    observeSnapshotSize(snapshot);

    if (snapshots.size() >= maxSnapshotCount) {
      // by the condition it's guaranteed there be a snapshot after skipping maxSnapshotCount - 1
      @SuppressWarnings("squid:S3655")
      final var oldest =
          snapshots.stream()
              .sorted(Comparator.reverseOrder())
              .skip(maxSnapshotCount - 1L)
              .findFirst()
              .get();

      LOGGER.info(
          "Max snapshot count reached ({}), purging snapshots older than {}",
          snapshots.size(),
          oldest);
      store.purgeSnapshots(oldest);

      final var optionalConverted = toSnapshot(oldest.getPath());
      if (optionalConverted.isPresent()) {
        final var converted = optionalConverted.get();
        deletionListeners.forEach(listener -> listener.onSnapshotsDeleted(converted));
      }
    }
  }

  @Override
  public void onSnapshotDeletion(
      final io.atomix.protocols.raft.storage.snapshot.Snapshot snapshot,
      final SnapshotStore store) {
    metrics.decrementSnapshotCount();
    LOGGER.debug("Snapshot {} removed from store {}", snapshot, store);
  }

  private Path getPendingDirectoryFor(
      final long index, final long term, final WallClockTimestamp timestamp, final long position) {
    final var pending = store.newPendingSnapshot(index, term, timestamp);
    final var pendingPath = pending.getPath();
    return pendingPath.resolveSibling(String.format("%s-%d", pendingPath.getFileName(), position));
  }

  private Optional<Snapshot> toSnapshot(final Path path) {
    return DbSnapshotMetadata.ofPath(path)
        .map(metadata -> new SnapshotImpl(metadata.getPosition(), path));
  }

  private void observeExistingSnapshots() {
    final var snapshots = store.getSnapshots();

    for (final var snapshot : snapshots) {
      observeSnapshotSize(snapshot);
    }

    metrics.setSnapshotCount(snapshots.size());
  }

  private void observeSnapshotSize(
      final io.atomix.protocols.raft.storage.snapshot.Snapshot snapshot) {
    try (final var contents = Files.newDirectoryStream(snapshot.getPath())) {
      var totalSize = 0L;

      for (final var path : contents) {
        if (Files.isRegularFile(path)) {
          final var size = Files.size(path);
          metrics.observeSnapshotFileSize(size);
          totalSize += size;
        }
      }

      metrics.observeSnapshotSize(totalSize);
    } catch (IOException e) {
      LOGGER.warn("Failed to observe size for snapshot {}", snapshot, e);
    }
  }
}
