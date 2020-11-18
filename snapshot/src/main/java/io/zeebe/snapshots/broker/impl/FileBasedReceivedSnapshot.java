/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.snapshots.broker.impl;

import static java.nio.file.StandardOpenOption.CREATE_NEW;

import io.zeebe.snapshots.raft.PersistedSnapshot;
import io.zeebe.snapshots.raft.ReceivedSnapshot;
import io.zeebe.snapshots.raft.SnapshotChunk;
import io.zeebe.util.ChecksumUtil;
import io.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileBasedReceivedSnapshot implements ReceivedSnapshot {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileBasedReceivedSnapshot.class);
  private static final boolean FAILED = false;
  private static final boolean SUCCESS = true;

  private final Path directory;
  private final FileBasedSnapshotStore snapshotStore;

  private ByteBuffer expectedId;
  private final FileBasedSnapshotMetadata metadata;
  private long expectedSnapshotChecksum;
  private int expectedTotalCount;

  FileBasedReceivedSnapshot(
      final FileBasedSnapshotMetadata metadata,
      final Path directory,
      final FileBasedSnapshotStore snapshotStore) {
    this.metadata = metadata;
    this.snapshotStore = snapshotStore;
    this.directory = directory;
    expectedSnapshotChecksum = Long.MIN_VALUE;
    expectedTotalCount = Integer.MIN_VALUE;
  }

  @Override
  public long index() {
    return metadata.getIndex();
  }

  @Override
  public boolean containsChunk(final ByteBuffer chunkId) {
    return Files.exists(directory.resolve(getFile(chunkId)));
  }

  @Override
  public boolean isExpectedChunk(final ByteBuffer chunkId) {
    if (expectedId == null) {
      return chunkId == null;
    }

    return expectedId.equals(chunkId);
  }

  @Override
  public void setNextExpected(final ByteBuffer nextChunkId) {
    expectedId = nextChunkId;
  }

  @Override
  public boolean apply(final SnapshotChunk snapshotChunk) throws IOException {
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

    final long expectedChecksum = snapshotChunk.getChecksum();
    final long actualChecksum = SnapshotChunkUtil.createChecksum(snapshotChunk.getContent());

    if (expectedChecksum != actualChecksum) {
      LOGGER.warn(
          "Expected to have checksum {} for snapshot chunk {} ({}), but calculated {}",
          expectedChecksum,
          chunkName,
          snapshotId,
          actualChecksum);
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
  public void abort() {
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
    }
  }

  @Override
  public PersistedSnapshot persist() {
    if (snapshotStore.hasSnapshotId(metadata.getSnapshotIdAsString())) {
      abort();
      return snapshotStore.getLatestSnapshot().orElseThrow();
    }

    final var files = directory.toFile().listFiles();
    Objects.requireNonNull(files, "No chunks have been applied yet");

    if (files.length != expectedTotalCount) {
      throw new IllegalStateException(
          String.format(
              "Expected '%d' chunk files for this snapshot, but found '%d'. Files are: %s.",
              expectedSnapshotChecksum, files.length, Arrays.toString(files)));
    }

    final var filePaths =
        Arrays.stream(files).sorted().map(File::toPath).collect(Collectors.toList());
    final long actualSnapshotChecksum;
    try {
      actualSnapshotChecksum = ChecksumUtil.createCombinedChecksum(filePaths);
    } catch (final IOException e) {
      throw new UncheckedIOException("Unexpected exception on calculating snapshot checksum.", e);
    }

    if (actualSnapshotChecksum != expectedSnapshotChecksum) {
      throw new IllegalStateException(
          String.format(
              "Expected snapshot checksum %d, but calculated %d.",
              expectedSnapshotChecksum, actualSnapshotChecksum));
    }

    return snapshotStore.newSnapshot(metadata, directory);
  }

  public Path getPath() {
    return directory;
  }

  private String getFile(final ByteBuffer chunkId) {
    final var view = new UnsafeBuffer(chunkId);
    return view.getStringWithoutLengthAscii(0, chunkId.remaining());
  }

  @Override
  public String toString() {
    return "FileBasedReceivedSnapshot{"
        + "directory="
        + directory
        + ", snapshotStore="
        + snapshotStore
        + ", expectedId="
        + expectedId
        + ", metadata="
        + metadata
        + '}';
  }
}
