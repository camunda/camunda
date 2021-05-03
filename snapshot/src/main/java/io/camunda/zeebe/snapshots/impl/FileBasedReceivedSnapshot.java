/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots.impl;

import static java.nio.file.StandardOpenOption.CREATE_NEW;

import io.zeebe.snapshots.PersistedSnapshot;
import io.zeebe.snapshots.ReceivedSnapshot;
import io.zeebe.snapshots.SnapshotChunk;
import io.zeebe.snapshots.SnapshotId;
import io.zeebe.util.FileUtil;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileBasedReceivedSnapshot implements ReceivedSnapshot {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileBasedReceivedSnapshot.class);
  private static final boolean FAILED = false;
  private static final boolean SUCCESS = true;

  private final Path directory;
  private final ActorControl actor;
  private final FileBasedSnapshotStore snapshotStore;

  private final FileBasedSnapshotMetadata metadata;
  private long expectedSnapshotChecksum;
  private int expectedTotalCount;

  FileBasedReceivedSnapshot(
      final FileBasedSnapshotMetadata metadata,
      final Path directory,
      final FileBasedSnapshotStore snapshotStore,
      final ActorControl actor) {
    this.metadata = metadata;
    this.snapshotStore = snapshotStore;
    this.directory = directory;
    this.actor = actor;
    expectedSnapshotChecksum = Long.MIN_VALUE;
    expectedTotalCount = Integer.MIN_VALUE;
  }

  @Override
  public long index() {
    return metadata.getIndex();
  }

  @Override
  public ActorFuture<Boolean> apply(final SnapshotChunk snapshotChunk) throws IOException {
    return actor.call(() -> applyInternal(snapshotChunk));
  }

  private boolean containsChunk(final String chunkId) {
    return Files.exists(directory.resolve(chunkId));
  }

  private boolean applyInternal(final SnapshotChunk snapshotChunk) throws IOException {
    if (containsChunk(snapshotChunk.getChunkName())) {
      return true;
    }

    final var currentSnapshotChecksum = snapshotChunk.getSnapshotChecksum();

    if (isSnapshotIdInvalid(snapshotChunk.getSnapshotId())) {
      return FAILED;
    }

    if (isSnapshotChecksumInvalid(currentSnapshotChecksum)) {
      return FAILED;
    }

    final var currentTotalCount = snapshotChunk.getTotalCount();
    if (isTotalCountInvalid(currentTotalCount)) {
      return FAILED;
    }

    final String snapshotId = snapshotChunk.getSnapshotId();
    final String chunkName = snapshotChunk.getChunkName();

    if (snapshotStore.hasSnapshotId(snapshotId)) {
      LOGGER.debug(
          "Ignore snapshot snapshotChunk {}, because snapshot {} already exists.",
          chunkName,
          snapshotId);
      return SUCCESS;
    }

    if (isChunkChecksumInvalid(snapshotChunk, snapshotId, chunkName)) {
      return FAILED;
    }

    final var tmpSnapshotDirectory = directory;
    FileUtil.ensureDirectoryExists(tmpSnapshotDirectory);

    final var snapshotFile = tmpSnapshotDirectory.resolve(chunkName);
    if (Files.exists(snapshotFile)) {
      LOGGER.debug("Received a snapshot snapshotChunk which already exist '{}'.", snapshotFile);
      return FAILED;
    }

    LOGGER.debug("Consume snapshot snapshotChunk {} of snapshot {}", chunkName, snapshotId);
    return writeReceivedSnapshotChunk(snapshotChunk, snapshotFile);
  }

  private boolean isChunkChecksumInvalid(
      final SnapshotChunk snapshotChunk, final String snapshotId, final String chunkName) {
    final long expectedChecksum = snapshotChunk.getChecksum();
    final long actualChecksum = SnapshotChunkUtil.createChecksum(snapshotChunk.getContent());

    if (expectedChecksum != actualChecksum) {
      LOGGER.warn(
          "Expected to have checksum {} for snapshot chunk {} ({}), but calculated {}",
          expectedChecksum,
          chunkName,
          snapshotId,
          actualChecksum);
      return true;
    }
    return false;
  }

  private boolean isSnapshotChecksumInvalid(final long currentSnapshotChecksum) {
    if (expectedSnapshotChecksum == Long.MIN_VALUE) {
      expectedSnapshotChecksum = currentSnapshotChecksum;
    }

    if (expectedSnapshotChecksum != currentSnapshotChecksum) {
      LOGGER.warn(
          "Expected snapshot chunk with equal snapshot checksum {}, but got chunk with snapshot checksum {}.",
          expectedSnapshotChecksum,
          currentSnapshotChecksum);
      return true;
    }
    return false;
  }

  private boolean isTotalCountInvalid(final int currentTotalCount) {
    if (expectedTotalCount == Integer.MIN_VALUE) {
      expectedTotalCount = currentTotalCount;
    }

    if (expectedTotalCount != currentTotalCount) {
      LOGGER.warn(
          "Expected snapshot chunk with equal snapshot total count {}, but got chunk with total count {}.",
          expectedTotalCount,
          currentTotalCount);
      return true;
    }
    return false;
  }

  private boolean isSnapshotIdInvalid(final String snapshotId) {
    final var receivedSnapshotId = FileBasedSnapshotMetadata.ofFileName(snapshotId);
    if (receivedSnapshotId.isEmpty()) {
      return true;
    }
    return metadata.compareTo(receivedSnapshotId.get()) != 0;
  }

  private boolean writeReceivedSnapshotChunk(
      final SnapshotChunk snapshotChunk, final Path snapshotFile) throws IOException {
    Files.write(snapshotFile, snapshotChunk.getContent(), CREATE_NEW, StandardOpenOption.WRITE);
    LOGGER.trace("Wrote replicated snapshot chunk to file {}", snapshotFile);
    return SUCCESS;
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

  private void abortInternal() {
    try {
      LOGGER.debug("DELETE dir {}", directory);
      FileUtil.deleteFolder(directory);
    } catch (final NoSuchFileException nsfe) {
      LOGGER.debug(
          "Tried to delete pending dir {}, but doesn't exist. Either was already removed or no chunk was applied until now.",
          directory,
          nsfe);
    } catch (final IOException e) {
      LOGGER.warn("Failed to delete pending snapshot {}", this, e);
    } finally {
      snapshotStore.removePendingSnapshot(this);
    }
  }

  private void persistInternal(final CompletableActorFuture<PersistedSnapshot> future) {
    if (snapshotStore.hasSnapshotId(metadata.getSnapshotIdAsString())) {
      abortInternal();
      future.complete(snapshotStore.getLatestSnapshot().orElseThrow());
      return;
    }

    final var files = directory.toFile().listFiles();
    try {
      Objects.requireNonNull(files, "No chunks have been applied yet");
    } catch (final Exception e) {
      future.completeExceptionally(e);
      return;
    }

    if (files.length != expectedTotalCount) {
      future.completeExceptionally(
          new IllegalStateException(
              String.format(
                  "Expected '%d' chunk files for this snapshot, but found '%d'. Files are: %s.",
                  expectedTotalCount, files.length, Arrays.toString(files))));
      return;
    }

    try {
      final PersistedSnapshot value =
          snapshotStore.newSnapshot(metadata, directory, expectedSnapshotChecksum);
      future.complete(value);
    } catch (final Exception e) {
      future.completeExceptionally(e);
    }
  }

  @Override
  public String toString() {
    return "FileBasedReceivedSnapshot{"
        + "directory="
        + directory
        + ", snapshotStore="
        + snapshotStore
        + ", metadata="
        + metadata
        + '}';
  }
}
