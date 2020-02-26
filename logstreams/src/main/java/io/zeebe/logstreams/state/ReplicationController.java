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
import org.agrona.collections.Object2NullableObjectHashMap;
import org.slf4j.Logger;

final class ReplicationController {
  private static final Logger LOG = Loggers.SNAPSHOT_LOGGER;
  private static final ReplicationContext INVALID_SNAPSHOT = new ReplicationContext(-1, -1);

  private final SnapshotReplication replication;
  private final Map<String, ReplicationContext> receivedSnapshots =
      new Object2NullableObjectHashMap<>();
  private final SnapshotReplicationMetrics metrics;

  private final SnapshotConsumer snapshotConsumer;

  ReplicationController(final SnapshotReplication replication, final SnapshotStorage storage) {
    this.replication = replication;
    this.snapshotConsumer = new FileSnapshotConsumer(storage, LOG);
    this.metrics = storage.getMetrics().getReplication();
    this.metrics.setCount(0);
  }

  void replicate(final String snapshotId, final int totalCount, final File snapshotChunkFile) {
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
  void consumeReplicatedSnapshots() {
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

    final ReplicationContext context =
        receivedSnapshots.computeIfAbsent(snapshotId, this::newReplication);
    if (context == INVALID_SNAPSHOT) {
      LOG.trace(
          "Ignore snapshot chunk {}, because snapshot {} is marked as invalid.",
          chunkName,
          snapshotId);
      return;
    }

    if (snapshotConsumer.consumeSnapshotChunk(snapshotChunk)) {
      validateWhenReceivedAllChunks(snapshotChunk, context);
    } else {
      markSnapshotAsInvalid(snapshotChunk);
    }
  }

  private void markSnapshotAsInvalid(final SnapshotChunk chunk) {
    snapshotConsumer.invalidateSnapshot(chunk.getSnapshotId());
    receivedSnapshots.put(chunk.getSnapshotId(), INVALID_SNAPSHOT);
    metrics.decrementCount();
  }

  private void validateWhenReceivedAllChunks(
      final SnapshotChunk snapshotChunk, final ReplicationContext context) {
    final int totalChunkCount = snapshotChunk.getTotalCount();
    context.chunkCount++;

    if (context.chunkCount == totalChunkCount) {
      LOG.debug(
          "Received all snapshot chunks ({}/{}), snapshot is valid",
          context.chunkCount,
          totalChunkCount);
      if (!tryToMarkSnapshotAsValid(snapshotChunk, context)) {
        LOG.debug("Failed to mark snapshot {} as valid", snapshotChunk.getSnapshotId());
      }
    } else {
      LOG.debug(
          "Waiting for more snapshot chunks, currently have {}/{}",
          context.chunkCount,
          totalChunkCount);
    }
  }

  private boolean tryToMarkSnapshotAsValid(
      final SnapshotChunk snapshotChunk, final ReplicationContext context) {
    if (snapshotConsumer.completeSnapshot(snapshotChunk.getSnapshotId())) {
      final var elapsed = System.currentTimeMillis() - context.startTimestamp;
      receivedSnapshots.remove(snapshotChunk.getSnapshotId());
      metrics.decrementCount();
      metrics.observeDuration(elapsed);

      return true;
    } else {
      markSnapshotAsInvalid(snapshotChunk);
      return false;
    }
  }

  private ReplicationContext newReplication(final String ignored) {
    final var context = new ReplicationContext(0L, System.currentTimeMillis());
    metrics.incrementCount();
    return context;
  }

  private static final class ReplicationContext {
    private final long startTimestamp;
    private long chunkCount;

    private ReplicationContext(final long chunkCount, final long startTimestamp) {
      this.chunkCount = chunkCount;
      this.startTimestamp = startTimestamp;
    }
  }
}
