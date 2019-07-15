/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.engine.impl;

import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.cluster.messaging.Subscription;
import io.zeebe.engine.Loggers;
import io.zeebe.logstreams.state.SnapshotChunk;
import io.zeebe.logstreams.state.SnapshotReplication;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class StateReplication implements SnapshotReplication {

  public static final String REPLICATION_TOPIC_FORMAT = "replication-%d";
  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  private final String replicationTopic;

  private final DirectBuffer readBuffer = new UnsafeBuffer(0, 0);
  private final ClusterEventService eventService;

  private ExecutorService executorService;
  private Subscription subscription;

  public StateReplication(ClusterEventService eventService, int partitionId) {
    this.eventService = eventService;
    this.replicationTopic = String.format(REPLICATION_TOPIC_FORMAT, partitionId);
  }

  @Override
  public void replicate(SnapshotChunk snapshot) {
    eventService.broadcast(
        replicationTopic,
        snapshot,
        (s) -> {
          LOG.debug(
              "Replicate on topic {} snapshot chunk {} for snapshot pos {}.",
              replicationTopic,
              s.getChunkName(),
              s.getSnapshotPosition());

          final SnapshotChunkImpl chunkImpl = new SnapshotChunkImpl(s);
          return chunkImpl.toBytes();
        });
  }

  @Override
  public void consume(Consumer<SnapshotChunk> consumer) {
    executorService = Executors.newSingleThreadExecutor((r) -> new Thread(r, replicationTopic));

    subscription =
        eventService
            .subscribe(
                replicationTopic,
                (bytes -> {
                  readBuffer.wrap(bytes);
                  final SnapshotChunkImpl chunk = new SnapshotChunkImpl();
                  chunk.wrap(readBuffer, 0, bytes.length);
                  LOG.debug(
                      "Received on topic {} replicated snapshot chunk {} for snapshot pos {}.",
                      replicationTopic,
                      chunk.getChunkName(),
                      chunk.getSnapshotPosition());
                  return chunk;
                }),
                consumer,
                executorService)
            .join();
  }

  @Override
  public void close() {
    if (subscription != null) {
      subscription.close().join();
      subscription = null;
    }
    if (executorService != null) {
      executorService.shutdownNow();
      executorService = null;
    }
  }
}
