/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.state;

import static java.nio.file.StandardOpenOption.CREATE_NEW;

import io.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.agrona.IoUtil;
import org.slf4j.Logger;

public class FileSnapshotConsumer implements SnapshotConsumer {

  private final SnapshotStorage storage;
  private final Logger logger;

  public FileSnapshotConsumer(SnapshotStorage storage, Logger logger) {
    this.storage = storage;
    this.logger = logger;
  }

  @Override
  public boolean consumeSnapshotChunk(SnapshotChunk chunk) {
    return writeChunkToDisk(chunk, storage);
  }

  @Override
  public boolean completeSnapshot(String snapshotId) {
    return storage.commitSnapshot(storage.getPendingDirectoryFor(snapshotId));
  }

  @Override
  public void invalidateSnapshot(String snapshotId) {
    final var pendingDirectory = storage.getPendingDirectoryFor(snapshotId);
    try {
      if (Files.exists(pendingDirectory)) {
        FileUtil.deleteFolder(pendingDirectory);
      }
    } catch (IOException e) {
      logger.debug("Could not delete temporary snapshot directory {}", pendingDirectory, e);
    }
  }

  private boolean writeChunkToDisk(SnapshotChunk snapshotChunk, SnapshotStorage storage) {
    final String snapshotId = snapshotChunk.getSnapshotId();
    final String chunkName = snapshotChunk.getChunkName();

    if (storage.exists(snapshotId)) {
      logger.debug(
          "Ignore snapshot chunk {}, because snapshot {} already exists.", chunkName, snapshotId);
      return true;
    }

    final long expectedChecksum = snapshotChunk.getChecksum();
    final long actualChecksum = SnapshotChunkUtil.createChecksum(snapshotChunk.getContent());

    if (expectedChecksum != actualChecksum) {
      logger.warn(
          "Expected to have checksum {} for snapshot chunk file {} ({}), but calculated {}",
          expectedChecksum,
          chunkName,
          snapshotId,
          actualChecksum);
      return false;
    }

    final var tmpSnapshotDirectory = storage.getPendingDirectoryFor(snapshotId);
    IoUtil.ensureDirectoryExists(tmpSnapshotDirectory.toFile(), "Temporary snapshot directory");

    final var snapshotFile = tmpSnapshotDirectory.resolve(chunkName);
    if (Files.exists(snapshotFile)) {
      logger.debug("Received a snapshot chunk which already exist '{}'.", snapshotFile);
      return false;
    }

    logger.debug("Consume snapshot chunk {} of snapshot {}", chunkName, snapshotId);
    return writeReceivedSnapshotChunk(snapshotChunk, snapshotFile);
  }

  private boolean writeReceivedSnapshotChunk(SnapshotChunk snapshotChunk, Path snapshotFile) {
    try {
      Files.write(snapshotFile, snapshotChunk.getContent(), CREATE_NEW, StandardOpenOption.WRITE);
      logger.trace("Wrote replicated snapshot chunk to file {}", snapshotFile);
      return true;
    } catch (IOException ioe) {
      logger.error(
          "Unexpected error occurred on writing snapshot chunk to '{}'.", snapshotFile, ioe);
      return false;
    }
  }
}
