/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.engine.impl;

import static io.zeebe.util.sched.Actor.actorNamePattern;

import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.cluster.messaging.Subscription;
import io.zeebe.engine.Loggers;
import io.zeebe.logstreams.state.SnapshotChunk;
import io.zeebe.logstreams.state.SnapshotReplication;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public final class StateReplication implements SnapshotReplication {

  public static final String REPLICATION_TOPIC_FORMAT = "replication-%d";
  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  private final String replicationTopic;

  private final DirectBuffer readBuffer = new UnsafeBuffer(0, 0);
  private final ClusterEventService eventService;
  private final String threadName;

  private ExecutorService executorService;
  private Subscription subscription;

  public StateReplication(
      final ClusterEventService eventService, final int partitionId, final int nodeId) {
    this.eventService = eventService;
    this.replicationTopic = String.format(REPLICATION_TOPIC_FORMAT, partitionId);
    this.threadName = actorNamePattern(nodeId, "StateReplication-" + partitionId);
  }

  @Override
  public void replicate(final SnapshotChunk snapshot) {
    eventService.broadcast(
        replicationTopic,
        snapshot,
        (s) -> {
          LOG.trace(
              "Replicate on topic {} snapshot chunk {} for snapshot {}.",
              replicationTopic,
              s.getChunkName(),
              s.getSnapshotId());

          final SnapshotChunkImpl chunkImpl = new SnapshotChunkImpl(s);
          return chunkImpl.toBytes();
        });
  }

  @Override
  public void consume(final Consumer<SnapshotChunk> consumer) {
    executorService = Executors.newSingleThreadExecutor((r) -> new Thread(r, threadName));

    subscription =
        eventService
            .subscribe(
                replicationTopic,
                (bytes -> {
                  readBuffer.wrap(bytes);
                  final SnapshotChunkImpl chunk = new SnapshotChunkImpl();
                  chunk.wrap(readBuffer, 0, bytes.length);
                  LOG.trace(
                      "Received on topic {} replicated snapshot chunk {} for snapshot {}.",
                      replicationTopic,
                      chunk.getChunkName(),
                      chunk.getSnapshotId());
                  return chunk;
                }),
                consumer,
                executorService)
            .join();
  }

  @Override
  public void close() throws Exception {
    if (subscription != null) {
      subscription.close().join();
      subscription = null;
    }

    if (executorService != null) {
      executorService.shutdownNow();
      executorService.awaitTermination(10, TimeUnit.SECONDS);
      executorService = null;
    }
  }
}
