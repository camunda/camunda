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
import io.atomix.protocols.raft.zeebe.ZeebeEntry;
import io.atomix.storage.journal.Indexed;
import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.broker.clustering.atomix.storage.AtomixRecordEntrySupplier;
import io.zeebe.logstreams.state.Snapshot;
import io.zeebe.logstreams.state.SnapshotDeletionListener;
import io.zeebe.logstreams.state.SnapshotMetrics;
import io.zeebe.logstreams.state.SnapshotStorage;
import io.zeebe.util.ZbLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
  private final Set<SnapshotDeletionListener> deletionListeners;
  private final SnapshotMetrics metrics;

  public AtomixSnapshotStorage(
      final Path runtimeDirectory,
      final SnapshotStore store,
      final AtomixRecordEntrySupplier entrySupplier,
      final SnapshotMetrics metrics) {
    this.runtimeDirectory = runtimeDirectory;
    this.entrySupplier = entrySupplier;
    this.store = store;
    this.metrics = metrics;

    this.deletionListeners = new CopyOnWriteArraySet<>();
    this.store.addListener(this);

    observeExistingSnapshots();
  }

  @Override
  public Optional<Snapshot> getPendingSnapshotFor(final long snapshotPosition) {
    final var optionalIndexed = entrySupplier.getIndexedEntry(snapshotPosition);
    return optionalIndexed.map(this::getSnapshot);
  }

  @Override
  public Optional<Path> getPendingDirectoryFor(final String id) {
    final var optionalMeta = DbSnapshotMetadata.ofFileName(id);
    return optionalMeta.map(this::getPendingDirectoryFor);
  }

  @Override
  public Optional<Snapshot> commitSnapshot(final Path snapshotPath) {
    final var metadata = DbSnapshotMetadata.ofPath(snapshotPath);
    return metadata.flatMap(m -> createNewCommittedSnapshot(snapshotPath, m));
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
    metrics.incrementSnapshotCount();
    observeSnapshotSize(snapshot);

    LOGGER.debug("Purging snapshots older than {}", snapshot);
    store.purgeSnapshots(snapshot);

    final var optionalConverted = toSnapshot(snapshot.getPath());
    if (optionalConverted.isPresent()) {
      final var converted = optionalConverted.get();
      // TODO #4067(@korthout): rename onSnapshotsDeleted, because it doesn't always delete
      deletionListeners.forEach(listener -> listener.onSnapshotsDeleted(converted));
    }
  }

  @Override
  public void onSnapshotDeletion(
      final io.atomix.protocols.raft.storage.snapshot.Snapshot snapshot,
      final SnapshotStore store) {
    metrics.decrementSnapshotCount();
    LOGGER.debug("Snapshot {} removed from store {}", snapshot, store);
  }

  private Optional<Snapshot> createNewCommittedSnapshot(
      final Path snapshotPath, final DbSnapshotMetadata metadata) {
    try (final var created =
        store.newSnapshot(
            metadata.getIndex(), metadata.getTerm(), metadata.getTimestamp(), snapshotPath)) {
      return Optional.of(new SnapshotImpl(metadata.getIndex(), created.getPath()));
    } catch (final UncheckedIOException e) {
      LOGGER.error("Failed to commit pending snapshot {} located at {}", metadata, snapshotPath, e);
      return Optional.empty();
    }
  }

  private Path getPendingDirectoryFor(final DbSnapshotMetadata metadata) {
    return getPendingDirectoryFor(metadata.getIndex(), metadata.getTerm(), metadata.getTimestamp());
  }

  private Path getPendingDirectoryFor(
      final long index, final long term, final WallClockTimestamp timestamp) {
    return store.newPendingSnapshot(index, term, timestamp).getPath();
  }

  private Optional<Snapshot> toSnapshot(final Path path) {
    return DbSnapshotMetadata.ofPath(path)
        .map(metadata -> new SnapshotImpl(metadata.getIndex(), path));
  }

  private void observeExistingSnapshots() {
    final var snapshots = store.getSnapshots();

    for (final var snapshot : snapshots) {
      observeSnapshotSize(snapshot);
    }

    metrics.setSnapshotCount(snapshots.size());
  }

  private Snapshot getSnapshot(final Indexed<ZeebeEntry> indexed) {
    final var pending =
        store.newPendingSnapshot(
            indexed.index(),
            indexed.entry().term(),
            WallClockTimestamp.from(System.currentTimeMillis()));
    return new SnapshotImpl(indexed.index(), pending.getPath());
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
    } catch (final IOException e) {
      LOGGER.warn("Failed to observe size for snapshot {}", snapshot, e);
    }
  }
}
