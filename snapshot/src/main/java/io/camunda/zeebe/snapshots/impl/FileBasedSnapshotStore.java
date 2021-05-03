/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots.impl;

import io.zeebe.snapshots.ConstructableSnapshotStore;
import io.zeebe.snapshots.PersistableSnapshot;
import io.zeebe.snapshots.PersistedSnapshot;
import io.zeebe.snapshots.PersistedSnapshotListener;
import io.zeebe.snapshots.ReceivableSnapshotStore;
import io.zeebe.snapshots.SnapshotId;
import io.zeebe.snapshots.TransientSnapshot;
import io.zeebe.util.FileUtil;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileBasedSnapshotStore extends Actor
    implements ConstructableSnapshotStore, ReceivableSnapshotStore {
  // first is the metadata and the second the the received snapshot count
  private static final String RECEIVING_DIR_FORMAT = "%s-%d";

  private static final Logger LOGGER = LoggerFactory.getLogger(FileBasedSnapshotStore.class);
  private static final String CHECKSUM_SUFFIX = ".checksum";

  // the root snapshotsDirectory where all snapshots should be stored
  private final Path snapshotsDirectory;
  // the root snapshotsDirectory when pending snapshots should be stored
  private final Path pendingDirectory;
  // keeps track of all snapshot modification listeners
  private final Set<PersistedSnapshotListener> listeners;

  private final SnapshotMetrics snapshotMetrics;

  // Use AtomicReference so that getting latest snapshot doesn't have to go through the actor
  private final AtomicReference<FileBasedSnapshot> currentPersistedSnapshotRef =
      new AtomicReference<>();
  // used to write concurrently received snapshots in different pending directories
  private final AtomicLong receivingSnapshotStartCount;
  private final Set<PersistableSnapshot> pendingSnapshots = new HashSet<>();
  private final String actorName;

  public FileBasedSnapshotStore(
      final int nodeId,
      final int partitionId,
      final SnapshotMetrics snapshotMetrics,
      final Path snapshotsDirectory,
      final Path pendingDirectory) {
    this.snapshotsDirectory = snapshotsDirectory;
    this.pendingDirectory = pendingDirectory;
    this.snapshotMetrics = snapshotMetrics;
    receivingSnapshotStartCount = new AtomicLong();

    listeners = new CopyOnWriteArraySet<>();
    actorName = buildActorName(nodeId, "SnapshotStore", partitionId);
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  protected void onActorStarting() {
    currentPersistedSnapshotRef.set(loadLatestSnapshot(snapshotsDirectory));
    purgePendingSnapshotsDirectory();
  }

  @Override
  protected void onActorClosing() {
    listeners.clear();
  }

  private FileBasedSnapshot loadLatestSnapshot(final Path snapshotDirectory) {
    FileBasedSnapshot latestPersistedSnapshot = null;
    final List<FileBasedSnapshot> snapshots = new ArrayList<>();
    try (final var stream =
        Files.newDirectoryStream(
            snapshotDirectory, p -> !p.getFileName().toString().endsWith(CHECKSUM_SUFFIX))) {
      for (final var path : stream) {
        final var snapshot = collectSnapshot(path);
        if (snapshot != null) {
          snapshots.add(snapshot);
          if ((latestPersistedSnapshot == null)
              || snapshot.getMetadata().compareTo(latestPersistedSnapshot.getMetadata()) >= 0) {
            latestPersistedSnapshot = snapshot;
          }
        }
      }
      // Delete older snapshots
      if (latestPersistedSnapshot != null) {
        snapshots.remove(latestPersistedSnapshot);
        if (!snapshots.isEmpty()) {
          LOGGER.debug("Purging snapshots older than {}", latestPersistedSnapshot);
          snapshots.forEach(
              oldSnapshot -> {
                LOGGER.debug("Deleting snapshot {}", oldSnapshot);
                oldSnapshot.delete();
              });
        }
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return latestPersistedSnapshot;
  }

  // TODO(npepinpe): using Either here would improve readability and observability, as validation
  //  can have better error messages, and the return type better expresses what we attempt to do,
  //  i.e. either it failed (with an error) or it succeeded
  private FileBasedSnapshot collectSnapshot(final Path path) throws IOException {
    final var optionalMeta = FileBasedSnapshotMetadata.ofPath(path);
    if (optionalMeta.isEmpty()) {
      return null;
    }

    final var metadata = optionalMeta.get();
    final var checksumPath = buildSnapshotsChecksumPath(metadata);

    if (!Files.exists(checksumPath)) {
      // checksum was not completely/successfully written, we can safely delete it and proceed
      LOGGER.debug(
          "Snapshot {} does not have a checksum file, which most likely indicates a partial write"
              + " (e.g. crash during move), and will be deleted",
          path);
      try {
        FileUtil.deleteFolder(path);
      } catch (final Exception e) {
        // it's fine to ignore failures to delete here, as it would constitute mostly noise
        LOGGER.debug("Failed to delete partial snapshot {}", path, e);
      }

      return null;
    }

    try {
      final var expectedChecksum = SnapshotChecksum.read(checksumPath);
      final var actualChecksum = SnapshotChecksum.calculate(path);
      if (expectedChecksum != actualChecksum) {
        LOGGER.warn(
            "Expected snapshot {} to have checksum {}, but the actual checksum is {}; the snapshot is most likely corrupted",
            path,
            expectedChecksum,
            actualChecksum);
        return null;
      }

      return new FileBasedSnapshot(path, checksumPath, actualChecksum, metadata);
    } catch (final Exception e) {
      LOGGER.warn("Could not load snapshot in {}", path, e);
      return null;
    }
  }

  private void purgePendingSnapshotsDirectory() {
    try (final var files = Files.list(pendingDirectory)) {
      files.filter(Files::isDirectory).forEach(this::purgePendingSnapshot);
    } catch (final IOException e) {
      LOGGER.error(
          "Failed to purge pending snapshots, which may result in unnecessary disk usage and should be monitored",
          e);
    }
  }

  @Override
  public boolean hasSnapshotId(final String id) {
    final var optLatestSnapshot = getLatestSnapshot();

    if (optLatestSnapshot.isPresent()) {
      final var snapshot = optLatestSnapshot.get();
      return snapshot.getPath().getFileName().toString().equals(id);
    }
    return false;
  }

  @Override
  public Optional<PersistedSnapshot> getLatestSnapshot() {
    return Optional.ofNullable(currentPersistedSnapshotRef.get());
  }

  @Override
  public ActorFuture<Void> purgePendingSnapshots() {
    final CompletableActorFuture<Void> abortFuture = new CompletableActorFuture<>();
    actor.run(
        () -> {
          final var abortedAll =
              pendingSnapshots.stream()
                  .map(PersistableSnapshot::abort)
                  .collect(Collectors.toList());
          actor.runOnCompletion(
              abortedAll,
              error -> {
                if (error == null) {
                  abortFuture.complete(null);
                } else {
                  abortFuture.completeExceptionally(error);
                }
              });
        });
    return abortFuture;
  }

  @Override
  public ActorFuture<Boolean> addSnapshotListener(final PersistedSnapshotListener listener) {
    return actor.call(() -> listeners.add(listener));
  }

  @Override
  public ActorFuture<Boolean> removeSnapshotListener(final PersistedSnapshotListener listener) {
    return actor.call(() -> listeners.remove(listener));
  }

  @Override
  public long getCurrentSnapshotIndex() {
    return getLatestSnapshot().map(PersistedSnapshot::getIndex).orElse(0L);
  }

  @Override
  public ActorFuture<Void> delete() {
    return actor.call(
        () -> {
          currentPersistedSnapshotRef.set(null);

          try {
            LOGGER.debug("DELETE FOLDER {}", snapshotsDirectory);
            FileUtil.deleteFolder(snapshotsDirectory);
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }

          try {
            LOGGER.debug("DELETE FOLDER {}", pendingDirectory);
            FileUtil.deleteFolder(pendingDirectory);
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }

  @Override
  public Path getPath() {
    return snapshotsDirectory;
  }

  @Override
  public FileBasedReceivedSnapshot newReceivedSnapshot(final String snapshotId) {
    final var optMetadata = FileBasedSnapshotMetadata.ofFileName(snapshotId);
    final var metadata =
        optMetadata.orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected snapshot id in a format like 'index-term-processedPosition-exportedPosition', got '"
                        + snapshotId
                        + "'."));

    // to make the pending dir unique
    final var nextStartCount = receivingSnapshotStartCount.incrementAndGet();
    final var pendingDirectoryName =
        String.format(RECEIVING_DIR_FORMAT, metadata.getSnapshotIdAsString(), nextStartCount);
    final var pendingSnapshotDir = pendingDirectory.resolve(pendingDirectoryName);
    final var newPendingSnapshot =
        new FileBasedReceivedSnapshot(metadata, pendingSnapshotDir, this, actor);
    addPendingSnapshot(newPendingSnapshot);
    return newPendingSnapshot;
  }

  // TODO(npepinpe): using Either here would improve readability and observability, as validation
  //  can have better error messages, and the return type better expresses what we attempt to do,
  //  i.e. either it failed (with an error) or it succeeded
  @Override
  public Optional<TransientSnapshot> newTransientSnapshot(
      final long index,
      final long term,
      final long processedPosition,
      final long exportedPosition) {

    final var newSnapshotId =
        new FileBasedSnapshotMetadata(index, term, processedPosition, exportedPosition);
    final FileBasedSnapshot currentSnapshot = currentPersistedSnapshotRef.get();
    if (currentSnapshot != null && currentSnapshot.getMetadata().compareTo(newSnapshotId) == 0) {
      LOGGER.debug(
          "Previous snapshot was taken for the same processed position {} and exported position {}, will not take snapshot.",
          processedPosition,
          exportedPosition);
      return Optional.empty();
    }
    final var directory = buildPendingSnapshotDirectory(newSnapshotId);

    final var newPendingSnapshot =
        new FileBasedTransientSnapshot(newSnapshotId, directory, this, actor);
    addPendingSnapshot(newPendingSnapshot);
    return Optional.of(newPendingSnapshot);
  }

  private void addPendingSnapshot(final PersistableSnapshot pendingSnapshot) {
    actor.call(() -> pendingSnapshots.add(pendingSnapshot));
  }

  void removePendingSnapshot(final PersistableSnapshot pendingSnapshot) {
    pendingSnapshots.remove(pendingSnapshot);
  }

  private void observeSnapshotSize(final FileBasedSnapshot persistedSnapshot) {
    try (final var contents = Files.newDirectoryStream(persistedSnapshot.getPath())) {
      var totalSize = 0L;
      var totalCount = 0L;
      for (final var path : contents) {
        if (Files.isRegularFile(path)) {
          final var size = Files.size(path);
          snapshotMetrics.observeSnapshotFileSize(size);
          totalSize += size;
          totalCount++;
        }
      }

      snapshotMetrics.observeSnapshotSize(totalSize);
      snapshotMetrics.observeSnapshotChunkCount(totalCount);
    } catch (final IOException e) {
      LOGGER.warn("Failed to observe size for snapshot {}", persistedSnapshot, e);
    }
  }

  private void purgePendingSnapshots(final SnapshotId cutoffIndex) {
    LOGGER.debug(
        "Search for orphaned snapshots below oldest valid snapshot with index {} in {}",
        cutoffIndex,
        pendingDirectory);

    pendingSnapshots.stream()
        .filter(pendingSnapshot -> pendingSnapshot.snapshotId().compareTo(cutoffIndex) < 0)
        .forEach(PersistableSnapshot::abort);

    // If there are orphaned directories if a previous abort failed, delete them explicitly
    try (final var pendingSnapshotsDirectories = Files.newDirectoryStream(pendingDirectory)) {
      for (final var pendingSnapshot : pendingSnapshotsDirectories) {
        purgePendingSnapshot(cutoffIndex, pendingSnapshot);
      }
    } catch (final IOException e) {
      LOGGER.warn(
          "Failed to delete orphaned snapshots, could not list pending directory {}",
          pendingDirectory,
          e);
    }
  }

  private void purgePendingSnapshot(final SnapshotId cutoffIndex, final Path pendingSnapshot) {
    final var optionalMetadata = FileBasedSnapshotMetadata.ofPath(pendingSnapshot);
    if (optionalMetadata.isPresent() && optionalMetadata.get().compareTo(cutoffIndex) < 0) {
      try {
        FileUtil.deleteFolder(pendingSnapshot);
        LOGGER.debug("Deleted orphaned snapshot {}", pendingSnapshot);
      } catch (final IOException e) {
        LOGGER.warn(
            "Failed to delete orphaned snapshot {}, risk using unnecessary disk space",
            pendingSnapshot,
            e);
      }
    }
  }

  private boolean isCurrentSnapshotNewer(final FileBasedSnapshotMetadata metadata) {
    final var persistedSnapshot = currentPersistedSnapshotRef.get();
    return (persistedSnapshot != null && persistedSnapshot.getMetadata().compareTo(metadata) >= 0);
  }

  // TODO(npepinpe): using Either here would allow easy rollback regardless of when or where an
  // exception is thrown, without having to catch and rollback for every possible case
  FileBasedSnapshot newSnapshot(
      final FileBasedSnapshotMetadata metadata, final Path directory, final long expectedChecksum) {
    final var currentPersistedSnapshot = currentPersistedSnapshotRef.get();

    if (isCurrentSnapshotNewer(metadata)) {
      LOGGER.debug("Snapshot is older then {} already exists", currentPersistedSnapshot);
      purgePendingSnapshots(metadata);
      return currentPersistedSnapshot;
    }

    // it's important to persist the checksum file only after the move is finished, since we use it
    // as a marker file to guarantee the move was complete and not partial
    final var destination = buildSnapshotDirectory(metadata);
    moveToSnapshotDirectory(directory, destination);

    final var checksumPath = buildSnapshotsChecksumPath(metadata);
    final long actualChecksum;
    try {
      // computing the checksum on the final destination also lets us detect any failures during the
      // copy/move that could occur
      actualChecksum = SnapshotChecksum.calculate(destination);
      if (actualChecksum != expectedChecksum) {
        rollbackPartialSnapshot(destination);
        throw new InvalidSnapshotChecksum(directory, expectedChecksum, actualChecksum);
      }

      SnapshotChecksum.persist(checksumPath, actualChecksum);
    } catch (final IOException e) {
      rollbackPartialSnapshot(destination);
      throw new UncheckedIOException(e);
    }

    final var newPersistedSnapshot =
        new FileBasedSnapshot(destination, checksumPath, actualChecksum, metadata);
    final var failed =
        !currentPersistedSnapshotRef.compareAndSet(currentPersistedSnapshot, newPersistedSnapshot);
    if (failed) {
      // we moved already the snapshot but we expected that this will be cleaned up by the next
      // successful snapshot
      final var errorMessage =
          "Expected that last snapshot is '%s', which should be replace with '%s', but last snapshot was '%s'.";
      throw new ConcurrentModificationException(
          String.format(
              errorMessage,
              currentPersistedSnapshot,
              newPersistedSnapshot,
              currentPersistedSnapshotRef.get()));
    }

    snapshotMetrics.incrementSnapshotCount();
    observeSnapshotSize(newPersistedSnapshot);

    LOGGER.debug("Purging snapshots older than {}", newPersistedSnapshot);
    if (currentPersistedSnapshot != null) {
      LOGGER.debug("Deleting snapshot {}", currentPersistedSnapshot);
      currentPersistedSnapshot.delete();
    }
    purgePendingSnapshots(newPersistedSnapshot.getMetadata());

    listeners.forEach(listener -> listener.onNewSnapshot(newPersistedSnapshot));

    LOGGER.debug("Created new snapshot {}", newPersistedSnapshot);
    return newPersistedSnapshot;
  }

  private void moveToSnapshotDirectory(final Path directory, final Path destination) {
    try {
      tryAtomicDirectoryMove(directory, destination);
    } catch (final FileAlreadyExistsException e) {
      LOGGER.debug(
          "Expected to move snapshot from {} to {}, but it already exists",
          directory,
          destination,
          e);
    } catch (final IOException e) {
      rollbackPartialSnapshot(destination);
      throw new UncheckedIOException(e);
    }
  }

  private void rollbackPartialSnapshot(final Path destination) {
    try {
      FileUtil.deleteFolderIfExists(destination);
    } catch (final IOException ioException) {
      LOGGER.debug(
          "Pending snapshot {} could not be deleted on rollback, but will be safely ignored as a "
              + "partial snapshot",
          destination,
          ioException);
    }
  }

  private void purgePendingSnapshot(final Path pendingSnapshot) {
    try {
      FileUtil.deleteFolder(pendingSnapshot);
      LOGGER.debug("Deleted not completed (orphaned) snapshot {}", pendingSnapshot);
    } catch (final IOException e) {
      LOGGER.warn("Failed to delete not completed (orphaned) snapshot {}", pendingSnapshot, e);
    }
  }

  private void tryAtomicDirectoryMove(final Path directory, final Path destination)
      throws IOException {
    try {
      Files.move(directory, destination, StandardCopyOption.ATOMIC_MOVE);
    } catch (final AtomicMoveNotSupportedException e) {
      LOGGER.warn("Atomic move not supported. Moving the snapshot files non-atomically.");
      Files.move(directory, destination);
    }
  }

  private Path buildPendingSnapshotDirectory(final SnapshotId id) {
    return pendingDirectory.resolve(id.getSnapshotIdAsString());
  }

  private Path buildSnapshotDirectory(final FileBasedSnapshotMetadata metadata) {
    return snapshotsDirectory.resolve(metadata.getSnapshotIdAsString());
  }

  private Path buildSnapshotsChecksumPath(final FileBasedSnapshotMetadata metadata) {
    return snapshotsDirectory.resolve(metadata.getSnapshotIdAsString() + CHECKSUM_SUFFIX);
  }

  SnapshotMetrics getSnapshotMetrics() {
    return snapshotMetrics;
  }
}
