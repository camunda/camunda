/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.ReceivedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotChunk;
import io.camunda.zeebe.snapshots.SnapshotId;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileBasedReceivedSnapshot implements ReceivedSnapshot {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileBasedReceivedSnapshot.class);
  private static final int BLOCK_SIZE = 512 * 1024;

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
  public ActorFuture<Void> apply(final SnapshotChunk snapshotChunk) {
    return actor.call(
        () -> {
          applyInternal(snapshotChunk);
          return null;
        });
  }

  private boolean containsChunk(final String chunkId) {
    return Files.exists(directory.resolve(chunkId));
  }

  private void applyInternal(final SnapshotChunk snapshotChunk) throws SnapshotWriteException {
    if (containsChunk(snapshotChunk.getChunkName())) {
      return;
    }

    checkSnapshotIdIsValid(snapshotChunk.getSnapshotId());

    final long currentSnapshotChecksum = snapshotChunk.getSnapshotChecksum();
    checkSnapshotChecksumIsValid(currentSnapshotChecksum);

    final var currentTotalCount = snapshotChunk.getTotalCount();
    checkTotalCountIsValid(currentTotalCount);

    final String snapshotId = snapshotChunk.getSnapshotId();
    final String chunkName = snapshotChunk.getChunkName();

    if (snapshotStore.hasSnapshotId(snapshotId)) {
      LOGGER.debug(
          "Ignore snapshot snapshotChunk {}, because snapshot {} already exists.",
          chunkName,
          snapshotId);
      return;
    }

    checkChunkChecksumIsValid(snapshotChunk, snapshotId, chunkName);

    final var tmpSnapshotDirectory = directory;
    try {
      FileUtil.ensureDirectoryExists(tmpSnapshotDirectory);
    } catch (final IOException e) {
      throw new SnapshotWriteException(
          String.format("Failed to ensure that directory %s exists.", tmpSnapshotDirectory), e);
    }

    final var snapshotFile = tmpSnapshotDirectory.resolve(chunkName);
    if (Files.exists(snapshotFile)) {
      throw new SnapshotWriteException(
          String.format(
              "Received a snapshot snapshotChunk which already exist '%s'.", snapshotFile));
    }

    LOGGER.trace("Consume snapshot snapshotChunk {} of snapshot {}", chunkName, snapshotId);
    writeReceivedSnapshotChunk(snapshotChunk, snapshotFile);
  }

  private void checkChunkChecksumIsValid(
      final SnapshotChunk snapshotChunk, final String snapshotId, final String chunkName)
      throws SnapshotWriteException {
    final long expectedChecksum = snapshotChunk.getChecksum();
    final long actualChecksum = SnapshotChunkUtil.createChecksum(snapshotChunk.getContent());

    if (expectedChecksum != actualChecksum) {
      throw new SnapshotWriteException(
          String.format(
              "Expected to have checksum %d for snapshot chunk %s (%s), but calculated %d",
              expectedChecksum, chunkName, snapshotId, actualChecksum));
    }
  }

  private void checkSnapshotChecksumIsValid(final long currentSnapshotChecksum)
      throws SnapshotWriteException {
    if (expectedSnapshotChecksum == Long.MIN_VALUE) {
      expectedSnapshotChecksum = currentSnapshotChecksum;
    }

    if (expectedSnapshotChecksum != currentSnapshotChecksum) {
      throw new SnapshotWriteException(
          String.format(
              "Expected snapshot chunk with equal snapshot checksum %d, but got chunk with snapshot checksum %d.",
              expectedSnapshotChecksum, currentSnapshotChecksum));
    }
  }

  private void checkTotalCountIsValid(final int currentTotalCount) throws SnapshotWriteException {
    if (expectedTotalCount == Integer.MIN_VALUE) {
      expectedTotalCount = currentTotalCount;
    }

    if (expectedTotalCount != currentTotalCount) {
      throw new SnapshotWriteException(
          String.format(
              "Expected snapshot chunk with equal snapshot total count %d, but got chunk with total count %d.",
              expectedTotalCount, currentTotalCount));
    }
  }

  private void checkSnapshotIdIsValid(final String snapshotId) throws SnapshotWriteException {
    final var receivedSnapshotId = FileBasedSnapshotMetadata.ofFileName(snapshotId);
    if (receivedSnapshotId.isEmpty()) {
      throw new SnapshotWriteException(
          String.format("Snapshot file name '%s' has unexpected format", snapshotId));
    }

    final FileBasedSnapshotMetadata chunkMetadata = receivedSnapshotId.get();
    if (metadata.compareTo(chunkMetadata) != 0) {
      throw new SnapshotWriteException(
          String.format(
              "Expected snapshot chunk metadata to match metadata '%s' but was '%s' instead",
              metadata, chunkMetadata));
    }
  }

  private void writeReceivedSnapshotChunk(
      final SnapshotChunk snapshotChunk, final Path snapshotFile) throws SnapshotWriteException {
    try (final var channel =
        FileChannel.open(snapshotFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      final ByteBuffer buffer = ByteBuffer.wrap(snapshotChunk.getContent());

      while (buffer.hasRemaining()) {
        final int newLimit = Math.min(buffer.capacity(), buffer.position() + BLOCK_SIZE);
        channel.write(buffer.limit(newLimit));
        buffer.limit(buffer.capacity());
      }

      channel.force(true);
    } catch (final IOException e) {
      throw new SnapshotWriteException(
          String.format("Failed to write snapshot chunk %s", snapshotChunk), e);
    }

    LOGGER.trace("Wrote replicated snapshot chunk to file {}", snapshotFile);
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
      LOGGER.debug("Aborting received snapshot in dir {}", directory);
      FileUtil.deleteFolderIfExists(directory);
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

    snapshotStore.removePendingSnapshot(this);
  }

  @Override
  public String toString() {
    return "FileBasedReceivedSnapshot{"
        + "directory="
        + directory
        + ", snapshotStore="
        + snapshotStore.getName()
        + ", metadata="
        + metadata
        + '}';
  }
}
