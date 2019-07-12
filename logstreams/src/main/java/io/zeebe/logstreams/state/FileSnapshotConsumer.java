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
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;

public class FileSnapshotConsumer implements SnapshotConsumer {

  private final StateStorage stateStorage;
  private final Logger logger;

  public FileSnapshotConsumer(StateStorage stateStorage, Logger logger) {
    this.stateStorage = stateStorage;
    this.logger = logger;
  }

  @Override
  public boolean consumeSnapshotChunk(SnapshotChunk chunk) {
    return writeChunkToDisk(chunk, stateStorage);
  }

  @Override
  public boolean completeSnapshot(long snapshotId) {
    return moveValidSnapshot(stateStorage, snapshotId);
  }

  @Override
  public void invalidateSnapshot(long snapshotId) {
    final File tmpSnapshotDirectory =
        stateStorage.getTmpSnapshotDirectoryFor(Long.toString(snapshotId));
    try {
      if (tmpSnapshotDirectory.exists()) {
        FileUtil.deleteFolder(tmpSnapshotDirectory.toPath());
      }
    } catch (IOException e) {
      logger.debug(
          "Could not delete temporary snapshot directory {}", tmpSnapshotDirectory.toPath());
    }
  }

  private boolean writeChunkToDisk(SnapshotChunk snapshotChunk, StateStorage storage) {
    final long snapshotPosition = snapshotChunk.getSnapshotPosition();
    final String snapshotName = Long.toString(snapshotPosition);
    final String chunkName = snapshotChunk.getChunkName();

    if (storage.existSnapshot(snapshotPosition)) {
      logger.debug(
          "Ignore snapshot chunk {}, because snapshot {} already exists.", chunkName, snapshotName);
      return true;
    }

    final long expectedChecksum = snapshotChunk.getChecksum();
    final long actualChecksum = SnapshotChunkUtil.createChecksum(snapshotChunk.getContent());

    if (expectedChecksum != actualChecksum) {
      logger.warn(
          "Expected to have checksum {} for snapshot chunk file {} ({}), but calculated {}",
          expectedChecksum,
          chunkName,
          snapshotName,
          actualChecksum);
      return false;
    }

    final File tmpSnapshotDirectory = storage.getTmpSnapshotDirectoryFor(snapshotName);
    if (!tmpSnapshotDirectory.exists()) {
      tmpSnapshotDirectory.mkdirs();
    }

    final File snapshotFile = new File(tmpSnapshotDirectory, chunkName);
    if (snapshotFile.exists()) {
      logger.debug("Received a snapshot chunk which already exist '{}'.", snapshotFile);
      return false;
    }

    logger.debug("Consume snapshot chunk {}", chunkName);
    return writeReceivedSnapshotChunk(snapshotChunk, snapshotFile);
  }

  private boolean writeReceivedSnapshotChunk(SnapshotChunk snapshotChunk, File snapshotFile) {
    try {
      Files.write(
          snapshotFile.toPath(), snapshotChunk.getContent(), CREATE_NEW, StandardOpenOption.WRITE);
      logger.trace("Wrote replicated snapshot chunk to file {}", snapshotFile.toPath());
      return true;
    } catch (IOException ioe) {
      logger.error(
          "Unexpected error occurred on writing snapshot chunk to '{}'.", snapshotFile, ioe);
      return false;
    }
  }

  private boolean moveValidSnapshot(StateStorage storage, long snapshotId) {
    final File validSnapshotDirectory = storage.getSnapshotDirectoryFor(snapshotId);
    final File tmpSnapshotDirectory = storage.getTmpSnapshotDirectoryFor(Long.toString(snapshotId));

    try {
      Files.move(tmpSnapshotDirectory.toPath(), validSnapshotDirectory.toPath());
      logger.debug("Moved snapshot {} to {}", snapshotId, validSnapshotDirectory.toPath());
      return true;
    } catch (FileAlreadyExistsException e) {
      return true;
    } catch (IOException ioe) {
      logger.error(
          "Unexpected error occurred when moving snapshot {} to {}",
          snapshotId,
          validSnapshotDirectory.toPath(),
          ioe);
      return false;
    }
  }
}
