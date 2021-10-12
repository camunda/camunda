/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotNotFoundException;
import io.camunda.zeebe.snapshots.SnapshotId;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.sched.ActorControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.io.IOException;
import java.nio.file.Path;
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
  private final ActorControl actor;
  private final FileBasedSnapshotStore snapshotStore;
  private final FileBasedSnapshotMetadata metadata;
  private final ActorFuture<Void> takenFuture = new CompletableActorFuture<>();
  private boolean isValid = false;
  private PersistedSnapshot snapshot;
  private long checksum;

  FileBasedTransientSnapshot(
      final FileBasedSnapshotMetadata metadata,
      final Path directory,
      final FileBasedSnapshotStore snapshotStore,
      final ActorControl actor) {
    this.metadata = metadata;
    this.snapshotStore = snapshotStore;
    this.directory = directory;
    this.actor = actor;
  }

  @Override
  public ActorFuture<Void> take(final Consumer<Path> takeSnapshot) {
    actor.run(() -> takeInternal(takeSnapshot));
    return takenFuture;
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
          checksum = SnapshotChecksum.calculate(directory);

          snapshot = null;
          isValid = true;
          takenFuture.complete(null);
        }

      } catch (final Exception exception) {
        LOGGER.warn("Unexpected exception on taking snapshot ({})", metadata, exception);
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
    actor.call(() -> persistInternal(future));
    return future;
  }

  @Override
  public SnapshotId snapshotId() {
    return metadata;
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
      snapshot = snapshotStore.newSnapshot(metadata, directory, checksum);
      future.complete(snapshot);
    } catch (final Exception e) {
      future.completeExceptionally(e);
    }

    snapshotStore.removePendingSnapshot(this);
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
        + ", checksum="
        + checksum
        + ", metadata="
        + metadata
        + '}';
  }
}
