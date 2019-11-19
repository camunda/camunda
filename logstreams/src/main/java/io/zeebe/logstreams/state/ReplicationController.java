/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.state;

import io.zeebe.logstreams.impl.Loggers;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.agrona.collections.Object2LongHashMap;
import org.slf4j.Logger;

public final class ReplicationController {

  private static final Logger LOG = Loggers.SNAPSHOT_LOGGER;

  private static final long START_VALUE = 0L;
  private static final long INVALID_SNAPSHOT = -1;
  private static final long MISSING_SNAPSHOT = Long.MIN_VALUE;

  private final SnapshotReplication replication;
  private final Map<String, Long> receivedSnapshots = new Object2LongHashMap<>(MISSING_SNAPSHOT);
  private final SnapshotStorage storage;

  private final SnapshotConsumer snapshotConsumer;

  public ReplicationController(
      final SnapshotReplication replication, final SnapshotStorage storage) {
    this.replication = replication;
    this.storage = storage;
    this.snapshotConsumer = new FileSnapshotConsumer(storage, LOG);
  }

  public void replicate(
      final String snapshotId, final int totalCount, final File snapshotChunkFile) {
    try {
      final SnapshotChunk chunkToReplicate =
          SnapshotChunkUtil.createSnapshotChunkFromFile(snapshotChunkFile, snapshotId, totalCount);
      replication.replicate(chunkToReplicate);
    } catch (final IOException ioe) {
      LOG.error(
          "Unexpected error on reading snapshot chunk from file '{}'.", snapshotChunkFile, ioe);
    }
  }

  /** Registering for consuming snapshot chunks. */
  public void consumeReplicatedSnapshots() {
    replication.consume(this::consumeSnapshotChunk);
  }

  /**
   * This is called by the snapshot replication implementation on each snapshot chunk
   *
   * @param snapshotChunk the chunk to consume
   */
  private void consumeSnapshotChunk(final SnapshotChunk snapshotChunk) {
    final String snapshotId = snapshotChunk.getSnapshotId();
    final String chunkName = snapshotChunk.getChunkName();

    final long snapshotCounter = receivedSnapshots.computeIfAbsent(snapshotId, k -> START_VALUE);
    if (snapshotCounter == INVALID_SNAPSHOT) {
      LOG.trace(
          "Ignore snapshot chunk {}, because snapshot {} is marked as invalid.",
          chunkName,
          snapshotId);
      return;
    }

    if (snapshotConsumer.consumeSnapshotChunk(snapshotChunk)) {
      validateWhenReceivedAllChunks(snapshotChunk);
    } else {
      markSnapshotAsInvalid(snapshotChunk);
    }
  }

  private void markSnapshotAsInvalid(final SnapshotChunk chunk) {
    receivedSnapshots.put(chunk.getSnapshotId(), INVALID_SNAPSHOT);
  }

  private void validateWhenReceivedAllChunks(final SnapshotChunk snapshotChunk) {
    final int totalChunkCount = snapshotChunk.getTotalCount();
    final long currentChunks = incrementAndGetChunkCount(snapshotChunk);

    if (currentChunks == totalChunkCount) {
      LOG.debug(
          "Received all snapshot chunks ({}/{}), snapshot is valid",
          currentChunks,
          totalChunkCount);

      final boolean valid = tryToMarkSnapshotAsValid(snapshotChunk);

      if (valid) {
        storage.commitSnapshot(storage.getPendingDirectoryFor(snapshotChunk.getSnapshotId()));
      }
    } else {
      LOG.trace(
          "Waiting for more snapshot chunks, currently have {}/{}", currentChunks, totalChunkCount);
    }
  }

  private long incrementAndGetChunkCount(final SnapshotChunk snapshotChunk) {
    final String snapshotId = snapshotChunk.getSnapshotId();
    final long oldCount = receivedSnapshots.get(snapshotId);
    final long newCount = oldCount + 1;
    receivedSnapshots.put(snapshotId, newCount);
    return newCount;
  }

  private boolean tryToMarkSnapshotAsValid(final SnapshotChunk snapshotChunk) {
    if (snapshotConsumer.completeSnapshot(snapshotChunk.getSnapshotId())) {
      receivedSnapshots.remove(snapshotChunk.getSnapshotId());
      return true;

    } else {
      markSnapshotAsInvalid(snapshotChunk);
      return false;
    }
  }
}
