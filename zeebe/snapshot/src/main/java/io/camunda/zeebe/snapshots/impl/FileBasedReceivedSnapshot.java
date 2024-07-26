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
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.ReceivedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotChunk;
import io.camunda.zeebe.snapshots.SnapshotId;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
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
  private final ConcurrencyControl actor;
  private final FileBasedSnapshotStoreImpl snapshotStore;

  private final FileBasedSnapshotId snapshotId;
  private long expectedSnapshotChecksum;
  private int expectedTotalCount;
  private FileBasedSnapshotMetadata metadata;
  private ByteBuffer metadataBuffer;
  private long writtenMetadataBytes;
  private SfvChecksumImpl checksumCollection;

  FileBasedReceivedSnapshot(
      final FileBasedSnapshotId snapshotId,
      final Path directory,
      final FileBasedSnapshotStoreImpl snapshotStore,
      final ConcurrencyControl actor) {
    this.snapshotId = snapshotId;
    this.snapshotStore = snapshotStore;
    this.directory = directory;
    this.actor = actor;
    expectedSnapshotChecksum = Long.MIN_VALUE;
    expectedTotalCount = Integer.MIN_VALUE;
    writtenMetadataBytes = 0;
  }

  @Override
  public long index() {
    return snapshotId.getIndex();
  }

  @Override
  public ActorFuture<Void> apply(final SnapshotChunk snapshotChunk) {
    return actor.call(
        () -> {
          applyInternal(snapshotChunk);
          return null;
        });
  }

  private void applyInternal(final SnapshotChunk snapshotChunk) throws SnapshotWriteException {
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

    LOGGER.trace("Consume snapshot snapshotChunk {} of snapshot {}", chunkName, snapshotId);
    writeReceivedSnapshotChunk(snapshotChunk, snapshotFile);

    if (checksumCollection == null) {
      checksumCollection = new SfvChecksumImpl();
    }
    checksumCollection.updateFromBytes(
        snapshotFile.getFileName().toString(), snapshotChunk.getContent());

    if (snapshotChunk.getChunkName().equals(FileBasedSnapshotStoreImpl.METADATA_FILE_NAME)) {
      try {
        collectMetadata(snapshotChunk);
      } catch (final IOException e) {
        throw new SnapshotWriteException("Cannot decode snapshot metadata");
      }
    }
  }

  private void collectMetadata(final SnapshotChunk chunk) throws IOException {
    if (metadataBuffer == null) {
      metadataBuffer = ByteBuffer.allocate(Math.toIntExact(chunk.getTotalFileSize()));
    }

    metadataBuffer.put(Math.toIntExact(chunk.getFileBlockPosition()), chunk.getContent());
    writtenMetadataBytes += chunk.getContent().length;

    if (writtenMetadataBytes == chunk.getTotalFileSize()) {
      metadata = FileBasedSnapshotMetadata.decode(metadataBuffer.array());
    }
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
    final var receivedSnapshotId = FileBasedSnapshotId.ofFileName(snapshotId);
    if (receivedSnapshotId.isEmpty()) {
      throw new SnapshotWriteException(
          String.format("Snapshot file name '%s' has unexpected format", snapshotId));
    }

    final FileBasedSnapshotId chunkSnapshotId = receivedSnapshotId.get();
    if (this.snapshotId.compareTo(chunkSnapshotId) != 0) {
      throw new SnapshotWriteException(
          String.format(
              "Expected snapshot id in chunk to be '%s' but was '%s' instead",
              this.snapshotId, chunkSnapshotId));
    }
  }

  private void writeReceivedSnapshotChunk(
      final SnapshotChunk snapshotChunk, final Path snapshotFile) throws SnapshotWriteException {

    try (final var channel =
        FileChannel.open(snapshotFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
      final ByteBuffer buffer = ByteBuffer.wrap(snapshotChunk.getContent());

      while (buffer.hasRemaining()) {
        final int newLimit = Math.min(buffer.capacity(), buffer.position() + BLOCK_SIZE);
        channel.position(snapshotChunk.getFileBlockPosition() + buffer.position());
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
    if (snapshotStore.hasSnapshotId(snapshotId.getSnapshotIdAsString())) {
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
      if (metadata == null) {
        // backward compatibility
        metadata =
            new FileBasedSnapshotMetadata(
                FileBasedSnapshotStoreImpl.VERSION,
                snapshotId.getProcessedPosition(),
                snapshotId.getExportedPosition(),
                Long.MAX_VALUE);
      }
      final PersistedSnapshot value =
          snapshotStore.persistNewSnapshot(snapshotId, checksumCollection, metadata);
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
        + ", metadata="
        + snapshotId
        + '}';
  }
}
