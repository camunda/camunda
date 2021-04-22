/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.partitions.impl;

import static io.zeebe.util.sched.Actor.buildActorName;

import io.atomix.raft.snapshot.impl.SnapshotChunkImpl;
import io.zeebe.broker.system.partitions.PartitionMessagingService;
import io.zeebe.broker.system.partitions.SnapshotReplication;
import io.zeebe.engine.Loggers;
import io.zeebe.snapshots.SnapshotChunk;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public final class StateReplication implements SnapshotReplication {

  private static final String REPLICATION_TOPIC_FORMAT = "replication-%d";
  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  private final String replicationTopic;

  private final DirectBuffer readBuffer = new UnsafeBuffer(0, 0);
  private final PartitionMessagingService messagingService;
  private final String threadName;

  private ExecutorService executorService;

  public StateReplication(
      final PartitionMessagingService messagingService, final int partitionId, final int nodeId) {
    this.messagingService = messagingService;
    replicationTopic = String.format(REPLICATION_TOPIC_FORMAT, partitionId);
    threadName = buildActorName(nodeId, "StateReplication", partitionId);
  }

  @Override
  public void replicate(final SnapshotChunk chunk) {
    LOG.trace(
        "Replicate on topic {} snapshot chunk {} for snapshot {}.",
        replicationTopic,
        chunk.getChunkName(),
        chunk.getSnapshotId());

    messagingService.broadcast(replicationTopic, serializeSnapshotChunk(chunk));
  }

  @Override
  public void consume(final Consumer<SnapshotChunk> consumer) {
    executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, threadName));
    messagingService.subscribe(
        replicationTopic,
        message -> {
          final var chunk = deserializeChunk(message);
          LOG.trace(
              "Received on topic {} replicated snapshot chunk {} for snapshot {}.",
              replicationTopic,
              chunk.getChunkName(),
              chunk.getSnapshotId());

          consumer.accept(chunk);
        },
        executorService);
  }

  @Override
  public void close() throws Exception {
    messagingService.unsubscribe(replicationTopic);

    if (executorService != null) {
      executorService.shutdownNow();
      executorService.awaitTermination(10, TimeUnit.SECONDS);
      executorService = null;
    }
  }

  private ByteBuffer serializeSnapshotChunk(final SnapshotChunk chunk) {
    return new SnapshotChunkImpl(chunk).toByteBuffer();
  }

  private SnapshotChunk deserializeChunk(final ByteBuffer buffer) {
    final var chunk = new SnapshotChunkImpl();
    readBuffer.wrap(buffer);
    chunk.wrap(readBuffer, 0, readBuffer.capacity());

    return chunk;
  }
}
