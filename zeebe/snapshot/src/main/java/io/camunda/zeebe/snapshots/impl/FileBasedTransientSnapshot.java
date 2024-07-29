/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.CRC32CChecksumProvider;
import io.camunda.zeebe.snapshots.MutableChecksumsSFV;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotNotFoundException;
import io.camunda.zeebe.snapshots.SnapshotId;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a pending snapshot, that is a snapshot in the process of being written and has not yet
 * been committed to the store.
 */
public final class FileBasedTransientSnapshot implements TransientSnapshot {
  private static final Logger LOGGER = LoggerFactory.getLogger(FileBasedTransientSnapshot.class);

  private final Path directory;
  private final ConcurrencyControl actor;
  private final FileBasedSnapshotStoreImpl snapshotStore;
  private final FileBasedSnapshotId snapshotId;
  private final ActorFuture<Void> takenFuture = new CompletableActorFuture<>();
  private boolean isValid = false;
  private PersistedSnapshot snapshot;
  private MutableChecksumsSFV checksum;
  private final CRC32CChecksumProvider checksumProvider;
  private long lastFollowupEventPosition = Long.MAX_VALUE;

  FileBasedTransientSnapshot(
      final FileBasedSnapshotId snapshotId,
      final Path directory,
      final FileBasedSnapshotStoreImpl snapshotStore,
      final ConcurrencyControl actor,
      final CRC32CChecksumProvider checksumProvider) {
    this.snapshotId = snapshotId;
    this.snapshotStore = snapshotStore;
    this.directory = directory;
    this.actor = actor;
    this.checksumProvider = checksumProvider;
  }

  @Override
  public ActorFuture<Void> take(final Consumer<Path> takeSnapshot) {
    actor.run(() -> takeInternal(takeSnapshot));
    return takenFuture;
  }

  @Override
  public TransientSnapshot withLastFollowupEventPosition(final long lastFollowupEventPosition) {
    actor.run(() -> this.lastFollowupEventPosition = lastFollowupEventPosition);
    return this;
  }

  private void takeInternal(final Consumer<Path> takeSnapshot) {
    final var snapshotMetrics = snapshotStore.getSnapshotMetrics();

    try (final var ignored = snapshotMetrics.startTimer()) {
      try {
        takeSnapshot.accept(getPath());
        if (!directory.toFile().exists() || directory.toFile().listFiles().length == 0) {
          // If no snapshot files are created, snapshot is not valid
          abortInternal();
          takenFuture.completeExceptionally(
              new IllegalStateException(
                  String.format(
                      "Expected to find transient snapshot in directory %s, but the directory is empty or does not exists",
                      directory)));

        } else {
          checksum = SnapshotChecksum.calculateWithProvidedChecksums(directory, checksumProvider);

          snapshot = null;
          isValid = true;
          takenFuture.complete(null);
        }

      } catch (final Exception exception) {
        LOGGER.warn("Unexpected exception on taking snapshot ({})", snapshotId, exception);
        abortInternal();
        takenFuture.completeExceptionally(exception);
      }
    }
  }

  @Override
  public ActorFuture<Void> abort() {
    final CompletableActorFuture<Void> abortFuture = new CompletableActorFuture<>();
    actor.run(
        () -> {
          abortInternal();
          abortFuture.complete(null);
        });
    return abortFuture;
  }

  @Override
  public ActorFuture<PersistedSnapshot> persist() {
    final CompletableActorFuture<PersistedSnapshot> future = new CompletableActorFuture<>();
    actor.call(
        () -> {
          persistInternal(future);
          return null;
        });
    return future;
  }

  @Override
  public SnapshotId snapshotId() {
    return snapshotId;
  }

  @Override
  public Path getPath() {
    return directory;
  }

  private void persistInternal(final CompletableActorFuture<PersistedSnapshot> future) {
    if (snapshot != null) {
      future.complete(snapshot);
      return;
    }

    if (!takenFuture.isDone() || takenFuture.isCompletedExceptionally()) {
      future.completeExceptionally(new IllegalStateException("Snapshot is not taken"));
      return;
    }

    if (!isValid) {
      future.completeExceptionally(
          new SnapshotNotFoundException("Snapshot may have been already deleted."));
      return;
    }

    try {
      final var metadata =
          new FileBasedSnapshotMetadata(
              FileBasedSnapshotStoreImpl.VERSION,
              snapshotId.getProcessedPosition(),
              snapshotId.getExportedPosition(),
              lastFollowupEventPosition);
      writeMetadataAndUpdateChecksum(metadata);
      snapshot = snapshotStore.persistNewSnapshot(snapshotId, checksum, metadata);
      future.complete(snapshot);
    } catch (final Exception e) {
      future.completeExceptionally(e);
    }

    snapshotStore.removePendingSnapshot(this);
  }

  private void writeMetadataAndUpdateChecksum(final FileBasedSnapshotMetadata metadata)
      throws IOException {
    final var metadataPath = directory.resolve(FileBasedSnapshotStoreImpl.METADATA_FILE_NAME);
    // Write metadata file along with snapshot files
    try (final var channel =
            FileChannel.open(
                metadataPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.DSYNC);
        final var output = Channels.newOutputStream(channel)) {
      metadata.encode(output);
      checksum.updateFromFile(metadataPath);
    }
  }

  private void abortInternal() {
    try {
      isValid = false;
      snapshot = null;
      LOGGER.debug("Aborting transient snapshot {}", this);
      FileUtil.deleteFolderIfExists(directory);
    } catch (final IOException e) {
      LOGGER.warn("Failed to delete pending snapshot {}", this, e);
    } finally {
      snapshotStore.removePendingSnapshot(this);
    }
  }

  @Override
  public String toString() {
    return "FileBasedTransientSnapshot{"
        + "directory="
        + directory
        + ", snapshotId="
        + snapshotId
        + ", checksum="
        + checksum
        + '}';
  }
}
