/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.PersistableSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotListener;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.snapshots.SnapshotException;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotAlreadyExistsException;
import io.camunda.zeebe.snapshots.SnapshotId;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.ActorThread;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Map;
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
  private final int partitionId;

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
    this.partitionId = partitionId;
  }

  @Override
  protected Map<String, String> createContext() {
    final var context = super.createContext();
    context.put(ACTOR_PROP_PARTITION_ID, Integer.toString(partitionId));
    return context;
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
    try (final var stream =
        Files.newDirectoryStream(
            snapshotDirectory, p -> !p.getFileName().toString().endsWith(CHECKSUM_SUFFIX))) {
      for (final var path : stream) {
        final var snapshot = collectSnapshot(path);
        if (snapshot != null) {
          if ((latestPersistedSnapshot == null)
              || snapshot.getMetadata().compareTo(latestPersistedSnapshot.getMetadata()) >= 0) {
            latestPersistedSnapshot = snapshot;
          }
        }
      }
      // Cleanup of the snapshot directory. Older or corrupted snapshots are deleted
      if (latestPersistedSnapshot != null) {
        cleanupSnapshotDirectory(snapshotDirectory, latestPersistedSnapshot);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return latestPersistedSnapshot;
  }

  private void cleanupSnapshotDirectory(
      final Path snapshotDirectory, final FileBasedSnapshot latestPersistedSnapshot)
      throws IOException {
    final var latestChecksumFile = latestPersistedSnapshot.getChecksumFile();
    final var latestDirectory = latestPersistedSnapshot.getDirectory();
    try (final var paths =
        Files.newDirectoryStream(
            snapshotDirectory, p -> !p.equals(latestDirectory) && !p.equals(latestChecksumFile))) {
      LOGGER.debug("Deleting snapshots other than {}", latestPersistedSnapshot.getId());
      paths.forEach(
          p -> {
            try {
              LOGGER.debug("Deleting {}", p);
              FileUtil.deleteFolderIfExists(p);
            } catch (final IOException e) {
              LOGGER.warn("Unable to delete {}", p, e);
            }
          });
    }
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
      if (expectedChecksum.getCombinedValue() != actualChecksum.getCombinedValue()) {
        LOGGER.warn(
            "Expected snapshot {} to have checksum {}, but the actual checksum is {}; the snapshot is most likely corrupted. The startup will fail if there is no other valid snapshot and the log has been compacted.",
            path,
            expectedChecksum.getCombinedValue(),
            actualChecksum.getCombinedValue());
        return null;
      }

      return new FileBasedSnapshot(path, checksumPath, actualChecksum.getCombinedValue(), metadata);
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
  public ActorFuture<Void> copySnapshot(
      final PersistedSnapshot snapshot, final Path targetDirectory) {
    final CompletableActorFuture<Void> result = new CompletableActorFuture<>();
    actor.run(
        () -> {
          if (!Files.exists(snapshot.getPath())) {
            result.completeExceptionally(
                String.format(
                    "Expected to copy snapshot %s to directory %s, but snapshot directory %s does not exists. Snapshot may have been deleted.",
                    snapshot.getId(), targetDirectory, snapshot.getPath()),
                new FileNotFoundException());
          } else {
            try {
              FileUtil.copySnapshot(snapshot.getPath(), targetDirectory);
              result.complete(null);
            } catch (final Exception e) {
              result.completeExceptionally(
                  String.format(
                      "Failed to copy snapshot %s to directory %s.",
                      snapshot.getId(), targetDirectory),
                  e);
            }
          }
        });

    return result;
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

  @Override
  public Either<SnapshotException, TransientSnapshot> newTransientSnapshot(
      final long index,
      final long term,
      final long processedPosition,
      final long exportedPosition) {

    final var newSnapshotId =
        new FileBasedSnapshotMetadata(index, term, processedPosition, exportedPosition);
    final FileBasedSnapshot currentSnapshot = currentPersistedSnapshotRef.get();
    if (currentSnapshot != null && currentSnapshot.getMetadata().compareTo(newSnapshotId) == 0) {
      final String error =
          String.format(
              "Previous snapshot was taken for the same processed position %d and exported position %d.",
              processedPosition, exportedPosition);
      return Either.left(new SnapshotAlreadyExistsException(error));
    }
    final var directory = buildPendingSnapshotDirectory(newSnapshotId);

    final var newPendingSnapshot =
        new FileBasedTransientSnapshot(newSnapshotId, directory, this, actor);
    addPendingSnapshot(newPendingSnapshot);
    return Either.right(newPendingSnapshot);
  }

  private void addPendingSnapshot(final PersistableSnapshot pendingSnapshot) {
    final Runnable action = () -> pendingSnapshots.add(pendingSnapshot);

    if (!isCurrentActor()) {
      actor.submit(action);
    } else {
      action.run();
    }
  }

  void removePendingSnapshot(final PersistableSnapshot pendingSnapshot) {
    pendingSnapshots.remove(pendingSnapshot);
  }

  private boolean isCurrentActor() {
    final var currentActorThread = ActorThread.current();

    if (currentActorThread != null) {
      final var task = currentActorThread.getCurrentTask();
      if (task != null) {
        return task.getActor() == this;
      }
    }

    return false;
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

  private void purgePendingSnapshots(final SnapshotId cutoffSnapshot) {
    LOGGER.trace(
        "Search for orphaned snapshots below oldest valid snapshot with index {} in {}",
        cutoffSnapshot.getSnapshotIdAsString(),
        pendingDirectory);

    pendingSnapshots.stream()
        .filter(pendingSnapshot -> pendingSnapshot.snapshotId().compareTo(cutoffSnapshot) < 0)
        .forEach(PersistableSnapshot::abort);

    // If there are orphaned directories if a previous abort failed, delete them explicitly
    try (final var pendingSnapshotsDirectories = Files.newDirectoryStream(pendingDirectory)) {
      for (final var pendingSnapshot : pendingSnapshotsDirectories) {
        purgePendingSnapshot(cutoffSnapshot, pendingSnapshot);
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
      final var currentPersistedSnapshotMetadata = currentPersistedSnapshot.getMetadata();

      LOGGER.debug(
          "Snapshot is older than the latest snapshot {}. Snapshot {} won't be committed.",
          currentPersistedSnapshotMetadata,
          metadata);

      purgePendingSnapshots(currentPersistedSnapshotMetadata);
      return currentPersistedSnapshot;
    }

    // it's important to persist the checksum file only after the move is finished, since we use it
    // as a marker file to guarantee the move was complete and not partial
    final var destination = buildSnapshotDirectory(metadata);
    moveToSnapshotDirectory(directory, destination);

    final var checksumPath = buildSnapshotsChecksumPath(metadata);
    final SfvChecksum actualChecksum;
    try {
      // computing the checksum on the final destination also lets us detect any failures during the
      // copy/move that could occur
      actualChecksum = SnapshotChecksum.calculate(destination);
      if (actualChecksum.getCombinedValue() != expectedChecksum) {
        rollbackPartialSnapshot(destination);
        throw new InvalidSnapshotChecksum(
            directory, expectedChecksum, actualChecksum.getCombinedValue());
      }

      SnapshotChecksum.persist(checksumPath, actualChecksum);
    } catch (final IOException e) {
      rollbackPartialSnapshot(destination);
      throw new UncheckedIOException(e);
    }

    final var newPersistedSnapshot =
        new FileBasedSnapshot(
            destination, checksumPath, actualChecksum.getCombinedValue(), metadata);
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
              newPersistedSnapshot.getMetadata(),
              currentPersistedSnapshotRef.get()));
    }

    LOGGER.info("Committed new snapshot {}", newPersistedSnapshot.getId());

    snapshotMetrics.incrementSnapshotCount();
    observeSnapshotSize(newPersistedSnapshot);

    LOGGER.trace(
        "Purging snapshots older than {}",
        newPersistedSnapshot.getMetadata().getSnapshotIdAsString());
    if (currentPersistedSnapshot != null) {
      LOGGER.debug(
          "Deleting previous snapshot {}",
          currentPersistedSnapshot.getMetadata().getSnapshotIdAsString());
      currentPersistedSnapshot.delete();
    }
    purgePendingSnapshots(newPersistedSnapshot.getMetadata());

    listeners.forEach(listener -> listener.onNewSnapshot(newPersistedSnapshot));

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
      FileUtil.moveDurably(directory, destination, StandardCopyOption.ATOMIC_MOVE);
    } catch (final AtomicMoveNotSupportedException e) {
      LOGGER.warn("Atomic move not supported. Moving the snapshot files non-atomically", e);
      FileUtil.moveDurably(directory, destination);
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
