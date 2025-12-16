/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import static io.camunda.zeebe.util.FileUtil.deleteFolder;
import static io.camunda.zeebe.util.FileUtil.ensureDirectoryExists;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.CRC32CChecksumProvider;
import io.camunda.zeebe.snapshots.ImmutableChecksumsSFV;
import io.camunda.zeebe.snapshots.PersistableSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotListener;
import io.camunda.zeebe.snapshots.SnapshotException;
import io.camunda.zeebe.snapshots.SnapshotException.CorruptedSnapshotException;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotAlreadyExistsException;
import io.camunda.zeebe.snapshots.SnapshotId;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileBasedSnapshotStoreImpl {
  public static final String SNAPSHOTS_DIRECTORY = "snapshots";
  public static final String PENDING_DIRECTORY = "pending";
  // When sorted with other files in the snapshot, the metadata file must be ordered at the end.
  // This is required for backward compatibility of checksum calculation. Otherwise, the older
  // versions, which are not aware of the metadata will calculate the checksum using a different
  // order of files. The  ordering requirement is fulfilled because the name "zeebe.metadata" is
  // lexicographically greater than all other snapshot files. We can change the name in later
  // versions, because the new checksum calculation already order the metadata file explicitly
  // instead of using the implicit sort order.
  public static final String METADATA_FILE_NAME = "zeebe.metadata";
  static final int VERSION = 1;
  // first is the metadata and the second the received snapshot count
  private static final Logger LOGGER = LoggerFactory.getLogger(FileBasedSnapshotStoreImpl.class);
  private static final String CHECKSUM_SUFFIX = ".checksum";
  private static final String TMP_CHECKSUM_SUFFIX = ".tmp";
  private final int brokerId;
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
  private final Set<FileBasedSnapshot> availableSnapshots = new HashSet<>();
  private final CRC32CChecksumProvider checksumProvider;
  private final ConcurrencyControl actor;

  public FileBasedSnapshotStoreImpl(
      final int brokerId,
      final Path root,
      final CRC32CChecksumProvider checksumProvider,
      final ConcurrencyControl actor,
      final SnapshotMetrics snapshotMetrics) {
    this.brokerId = brokerId;
    snapshotsDirectory = root.resolve(SNAPSHOTS_DIRECTORY);
    pendingDirectory = root.resolve(PENDING_DIRECTORY);
    this.actor = actor;
    this.snapshotMetrics = snapshotMetrics;

    try {
      FileUtil.ensureDirectoryExists(snapshotsDirectory);
      FileUtil.ensureDirectoryExists(pendingDirectory);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create snapshot directories", e);
    }

    receivingSnapshotStartCount = new AtomicLong();

    listeners = new CopyOnWriteArraySet<>();
    this.checksumProvider = Objects.requireNonNull(checksumProvider);
  }

  public void start() {
    final FileBasedSnapshot latestSnapshot = loadLatestSnapshot(snapshotsDirectory);
    currentPersistedSnapshotRef.set(latestSnapshot);
    if (latestSnapshot != null) {
      availableSnapshots.add(latestSnapshot);
    }
    purgePendingSnapshotsDirectory();
  }

  public void close() {
    listeners.clear();
  }

  private FileBasedSnapshot loadLatestSnapshot(final Path snapshotDirectory) {
    FileBasedSnapshot latestPersistedSnapshot = null;
    try (final var stream = Files.newDirectoryStream(snapshotDirectory, Files::isDirectory)) {
      for (final var path : stream) {
        final var snapshot = collectSnapshot(path);
        if (snapshot != null) {
          if ((latestPersistedSnapshot == null)
              || snapshot.getSnapshotId().compareTo(latestPersistedSnapshot.getSnapshotId()) >= 0) {
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
    final var latestChecksumFile = latestPersistedSnapshot.getChecksumPath();
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
    final var optionalMeta = FileBasedSnapshotId.ofPath(path);
    if (optionalMeta.isEmpty()) {
      return null;
    }

    final var snapshotId = optionalMeta.get();
    final var checksumPath = buildSnapshotsChecksumPath(snapshotId);

    if (!Files.exists(checksumPath)) {
      // checksum was not completely/successfully written, we can safely delete it and proceed
      LOGGER.debug(
          "Snapshot {} does not have a checksum file, which most likely indicates a partial write"
              + " (e.g. crash during move), and will be deleted",
          path);
      try {
        deleteFolder(path);
      } catch (final Exception e) {
        // it's fine to ignore failures to delete here, as it would constitute mostly noise
        LOGGER.debug("Failed to delete partial snapshot {}", path, e);
      }

      return null;
    }

    try {
      final var expectedChecksum = SnapshotChecksum.read(checksumPath);
      final var actualChecksum =
          SnapshotChecksum.calculateWithProvidedChecksums(path, checksumProvider);
      if (!actualChecksum.sameChecksums(expectedChecksum)) {
        LOGGER.warn(
            "Expected snapshot {} to have checksums {}, but the actual checksums are {}; the snapshot is most likely corrupted. The startup will fail if there is no other valid snapshot and the log has been compacted.",
            path,
            expectedChecksum.getChecksums(),
            actualChecksum.getChecksums());
        return null;
      }

      final var metadata = collectMetadata(path, snapshotId);
      return new FileBasedSnapshot(
          path, checksumPath, actualChecksum, snapshotId, metadata, this::onSnapshotDeleted, actor);
    } catch (final Exception e) {
      LOGGER.warn("Could not load snapshot in {}", path, e);
      return null;
    }
  }

  private FileBasedSnapshotMetadata collectMetadata(
      final Path path, final FileBasedSnapshotId snapshotId) throws IOException {
    final var metadataPath = path.resolve(METADATA_FILE_NAME);
    if (metadataPath.toFile().exists()) {
      final var encodedMetadata = Files.readAllBytes(metadataPath);
      return FileBasedSnapshotMetadata.decode(encodedMetadata);
    } else {
      // backward compatibility mode
      return new FileBasedSnapshotMetadata(
          VERSION,
          snapshotId.getProcessedPosition(),
          snapshotId.getExportedPosition(),
          Long.MAX_VALUE);
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

  public boolean hasSnapshotId(final String id) {
    final var optLatestSnapshot = getLatestSnapshot();

    if (optLatestSnapshot.isPresent()) {
      final var snapshot = optLatestSnapshot.get();
      return snapshot.getPath().getFileName().toString().equals(id);
    }
    return false;
  }

  public Optional<PersistedSnapshot> getLatestSnapshot() {
    return Optional.ofNullable(currentPersistedSnapshotRef.get());
  }

  public ActorFuture<Set<PersistedSnapshot>> getAvailableSnapshots() {
    // return a new set so that caller cannot modify availableSnapshot
    return actor.call(() -> Collections.unmodifiableSet(availableSnapshots));
  }

  public ActorFuture<Long> getCompactionBound() {
    return actor.call(
        () ->
            availableSnapshots.stream()
                .map(PersistedSnapshot::getCompactionBound)
                .min(Long::compareTo)
                .orElse(0L));
  }

  public ActorFuture<Void> purgePendingSnapshots() {
    final CompletableActorFuture<Void> abortFuture = new CompletableActorFuture<>();
    actor.run(
        () -> {
          final var abortedAll = pendingSnapshots.stream().map(PersistableSnapshot::abort).toList();
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

  public ActorFuture<Boolean> addSnapshotListener(final PersistedSnapshotListener listener) {
    return actor.call(() -> listeners.add(listener));
  }

  public ActorFuture<Boolean> removeSnapshotListener(final PersistedSnapshotListener listener) {
    return actor.call(() -> listeners.remove(listener));
  }

  public long getCurrentSnapshotIndex() {
    return getLatestSnapshot().map(PersistedSnapshot::getIndex).orElse(0L);
  }

  public ActorFuture<Void> delete() {
    return actor.call(
        () -> {
          currentPersistedSnapshotRef.set(null);

          try {
            LOGGER.debug("DELETE FOLDER {}", snapshotsDirectory);
            deleteFolder(snapshotsDirectory);
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }

          try {
            LOGGER.debug("DELETE FOLDER {}", pendingDirectory);
            deleteFolder(pendingDirectory);
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
          return null;
        });
  }

  public Path getPath() {
    return snapshotsDirectory;
  }

  public ActorFuture<FileBasedReceivedSnapshot> newReceivedSnapshot(final String snapshotId) {
    final var newSnapshotFuture = new CompletableActorFuture<FileBasedReceivedSnapshot>();
    final var optSnapshotId = FileBasedSnapshotId.ofFileName(snapshotId);
    final var parsedSnapshotId =
        optSnapshotId.orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected snapshot id in a format like 'index-term-processedPosition-exportedPosition', got '"
                        + snapshotId
                        + "'."));

    actor.run(
        () -> {
          final var directory = buildSnapshotDirectory(parsedSnapshotId);
          try {
            checkAndCleanupExistingDirectory(snapshotId, parsedSnapshotId, directory);
            createReceivedSnapshot(parsedSnapshotId, directory, newSnapshotFuture);
          } catch (final Exception e) {
            newSnapshotFuture.completeExceptionally(e);
          }
        });
    return newSnapshotFuture;
  }

  private void createReceivedSnapshot(
      final FileBasedSnapshotId parsedSnapshotId,
      final Path directory,
      final CompletableActorFuture<FileBasedReceivedSnapshot> newSnapshotFuture) {
    final var newPendingSnapshot =
        new FileBasedReceivedSnapshot(parsedSnapshotId, directory, this, actor);
    addPendingSnapshot(newPendingSnapshot);
    newSnapshotFuture.complete(newPendingSnapshot);
  }

  private void checkAndCleanupExistingDirectory(
      final String snapshotId, final FileBasedSnapshotId parsedSnapshotId, final Path directory) {
    if (directory.toFile().exists()) {
      if (!buildSnapshotsChecksumPath(parsedSnapshotId).toFile().exists()) {
        try {
          // old pending/incomplete received snapshots which we can delete
          FileUtil.deleteFolderIfExists(directory);
        } catch (final IOException e) {
          throw new IllegalStateException(
              "Expected to delete pending received snapshot, but failed.", e);
        }
      } else {
        // this should not happen
        // this means we persisted a snapshot - marked as valid
        // and now received the same snapshot via replication
        throw new SnapshotAlreadyExistsException(
            String.format(
                "Expected to receive snapshot with id %s, but was already persisted. This shouldn't happen.",
                snapshotId));
      }
    }
  }

  public Either<SnapshotException, TransientSnapshot> newTransientSnapshot(
      final long index,
      final long term,
      final long processedPosition,
      final long exportedPosition) {

    final var newSnapshotId =
        new FileBasedSnapshotId(index, term, processedPosition, exportedPosition, brokerId);
    final FileBasedSnapshot currentSnapshot = currentPersistedSnapshotRef.get();
    if (currentSnapshot != null && currentSnapshot.getSnapshotId().compareTo(newSnapshotId) == 0) {
      final String error =
          String.format(
              "Previous snapshot was taken for the same processed position %d and exported position %d.",
              processedPosition, exportedPosition);
      return Either.left(new SnapshotAlreadyExistsException(error));
    }
    // transient snapshots are directly written to our snapshot dir
    // with the sfv checksum file they are marked as valid
    final var directory = buildSnapshotDirectory(newSnapshotId);
    final var newPendingSnapshot =
        new FileBasedTransientSnapshot(newSnapshotId, directory, this, actor, checksumProvider);
    addPendingSnapshot(newPendingSnapshot);
    return Either.right(newPendingSnapshot);
  }

  private void addPendingSnapshot(final PersistableSnapshot pendingSnapshot) {
    final Runnable action = () -> pendingSnapshots.add(pendingSnapshot);
    actor.run(action);
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
    final var optionalMetadata = FileBasedSnapshotId.ofPath(pendingSnapshot);
    if (optionalMetadata.isPresent() && optionalMetadata.get().compareTo(cutoffIndex) < 0) {
      try {
        deleteFolder(pendingSnapshot);
        LOGGER.debug("Deleted orphaned snapshot {}", pendingSnapshot);
      } catch (final IOException e) {
        LOGGER.warn(
            "Failed to delete orphaned snapshot {}, risk using unnecessary disk space",
            pendingSnapshot,
            e);
      }
    }
  }

  private boolean isCurrentSnapshotNewer(final FileBasedSnapshotId snapshotId) {
    final var persistedSnapshot = currentPersistedSnapshotRef.get();
    return (persistedSnapshot != null
        && persistedSnapshot.getSnapshotId().compareTo(snapshotId) >= 0);
  }

  FileBasedSnapshot persistNewSnapshot(
      final FileBasedSnapshotId snapshotId,
      final ImmutableChecksumsSFV immutableChecksumsSFV,
      final FileBasedSnapshotMetadata metadata) {
    final var currentPersistedSnapshot = currentPersistedSnapshotRef.get();

    if (isCurrentSnapshotNewer(snapshotId)) {
      final var currentPersistedSnapshotId = currentPersistedSnapshot.getSnapshotId();

      LOGGER.debug(
          "Snapshot is older than the latest snapshot {}. Snapshot {} won't be committed.",
          currentPersistedSnapshotId,
          snapshotId);

      purgePendingSnapshots(currentPersistedSnapshotId);
      return currentPersistedSnapshot;
    }

    try (final var ignored = snapshotMetrics.startPersistTimer()) {
      // it's important to persist the checksum file only after the move is finished, since we use
      // it as a marker file to guarantee the move was complete and not partial
      final var destination = buildSnapshotDirectory(snapshotId);
      final var checksumPath = buildSnapshotsChecksumPath(snapshotId);
      final var tmpChecksumPath =
          checksumPath.resolveSibling(checksumPath.getFileName().toString() + TMP_CHECKSUM_SUFFIX);
      try {
        SnapshotChecksum.persist(tmpChecksumPath, immutableChecksumsSFV);
        FileUtil.moveDurably(tmpChecksumPath, checksumPath);
      } catch (final IOException e) {
        rollbackPartialSnapshot(destination);
        throw new UncheckedIOException(e);
      }

      final var newPersistedSnapshot =
          new FileBasedSnapshot(
              destination,
              checksumPath,
              immutableChecksumsSFV,
              snapshotId,
              metadata,
              this::onSnapshotDeleted,
              actor);
      final var failed =
          !currentPersistedSnapshotRef.compareAndSet(
              currentPersistedSnapshot, newPersistedSnapshot);
      if (failed) {
        // we moved already the snapshot but we expected that this will be cleaned up by the next
        // successful snapshot
        final var errorMessage =
            "Expected that last snapshot is '%s', which should be replace with '%s', but last snapshot was '%s'.";
        throw new ConcurrentModificationException(
            String.format(
                errorMessage,
                currentPersistedSnapshot,
                newPersistedSnapshot.getSnapshotId(),
                currentPersistedSnapshotRef.get()));
      }

      availableSnapshots.add(newPersistedSnapshot);

      LOGGER.info("Committed new snapshot {}", newPersistedSnapshot.getId());

      snapshotMetrics.incrementSnapshotCount();
      observeSnapshotSize(newPersistedSnapshot);

      deleteOlderSnapshots(newPersistedSnapshot);

      listeners.forEach(listener -> listener.onNewSnapshot(newPersistedSnapshot));

      return newPersistedSnapshot;
    }
  }

  private void deleteOlderSnapshots(final FileBasedSnapshot newPersistedSnapshot) {
    LOGGER.trace(
        "Purging snapshots older than {}",
        newPersistedSnapshot.getSnapshotId().getSnapshotIdAsString());
    final var snapshotsToDelete =
        availableSnapshots.stream()
            .filter(s -> !s.getId().equals(newPersistedSnapshot.getId()))
            .filter(s -> !s.isReserved())
            .toList();
    snapshotsToDelete.forEach(
        previousSnapshot -> {
          LOGGER.debug("Deleting previous snapshot {}", previousSnapshot.getId());
          previousSnapshot.delete();
        });
    purgePendingSnapshots(newPersistedSnapshot.getSnapshotId());
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
      deleteFolder(pendingSnapshot);
      LOGGER.debug("Deleted not completed (orphaned) snapshot {}", pendingSnapshot);
    } catch (final IOException e) {
      LOGGER.warn("Failed to delete not completed (orphaned) snapshot {}", pendingSnapshot, e);
    }
  }

  private Path buildSnapshotDirectory(final FileBasedSnapshotId snapshotId) {
    return snapshotsDirectory.resolve(snapshotId.getSnapshotIdAsString());
  }

  private Path buildSnapshotsChecksumPath(final FileBasedSnapshotId snapshotId) {
    return snapshotsDirectory.resolve(snapshotId.getSnapshotIdAsString() + CHECKSUM_SUFFIX);
  }

  private boolean isChecksumFile(final String name) {
    return name.endsWith(CHECKSUM_SUFFIX);
  }

  SnapshotMetrics getSnapshotMetrics() {
    return snapshotMetrics;
  }

  void onSnapshotDeleted(final FileBasedSnapshot snapshot) {
    availableSnapshots.remove(snapshot);
  }

  @Override
  public String toString() {
    return "FileBasedSnapshotStore{"
        + "snapshotsDirectory="
        + snapshotsDirectory
        + ", pendingDirectory="
        + pendingDirectory
        + ", listeners="
        + listeners
        + ", currentPersistedSnapshotRef="
        + currentPersistedSnapshotRef
        + ", receivingSnapshotStartCount="
        + receivingSnapshotStartCount
        + ", pendingSnapshots="
        + pendingSnapshots
        + ", availableSnapshots="
        + availableSnapshots
        + "}";
  }

  public void restore(final String snapshotId, final Map<String, Path> snapshotFiles)
      throws IOException {
    final var parsedSnapshotId =
        FileBasedSnapshotId.ofFileName(snapshotId)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Failed to parse snapshot id %s".formatted(snapshotId)));
    final var checksumPath = buildSnapshotsChecksumPath(parsedSnapshotId);
    final var snapshotPath = buildSnapshotDirectory(parsedSnapshotId);
    ensureDirectoryExists(snapshotPath);

    LOGGER.info("Moving snapshot {} to {}", snapshotId, snapshotPath);

    final var snapshotFileNames = snapshotFiles.keySet();
    snapshotFileNames.stream()
        .filter(name -> !isChecksumFile(name))
        .forEach(name -> copyNamedFileToDirectory(name, snapshotFiles.get(name), snapshotPath));

    final var checksumFile =
        snapshotFileNames.stream()
            .filter(this::isChecksumFile)
            .findFirst()
            .map(snapshotFiles::get)
            .orElseThrow();

    Files.copy(checksumFile, checksumPath);

    // Flush directory of this snapshot as well as root snapshot directory
    FileUtil.flushDirectory(snapshotPath);
    FileUtil.flushDirectory(snapshotsDirectory);

    LOGGER.info("Moved snapshot {} to {}", snapshotId, snapshotPath);

    // verify snapshot is not corrupted
    final var snapshot = collectSnapshot(snapshotPath);
    if (snapshot == null) {
      throw new CorruptedSnapshotException(
          "Failed to open restored snapshot in %s".formatted(snapshotPath));
    }
  }

  private void copyNamedFileToDirectory(
      final String name, final Path source, final Path targetDirectory) {
    final var targetFilePath = targetDirectory.resolve(name);
    try {
      Files.move(source, targetFilePath);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
