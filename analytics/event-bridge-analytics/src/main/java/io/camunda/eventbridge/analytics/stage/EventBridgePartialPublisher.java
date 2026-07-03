/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.EventBridgeClient.BatchPublisher;
import io.camunda.eventbridge.streaming.shuffle.Partial;
import io.camunda.eventbridge.streaming.shuffle.PartialCodec;
import io.camunda.eventbridge.streaming.shuffle.PartialPublisher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The event-bridge implementation of {@link PartialPublisher}: publishes Stage-1 partials to the
 * facts topic, routing each to a partition by {@code hash(aggId, key)} so all writers of a cell
 * land on one partition (single-writer per cell downstream). {@link #publish} buffers; {@link
 * #flush} sends one batch per target partition and blocks until all are durable
 * (produce-before-checkpoint).
 */
public final class EventBridgePartialPublisher implements PartialPublisher {

  private final EventBridgeClient client;
  private final String topic;
  private final int partitionCount;
  private final Map<Integer, BatchPublisher> batches = new HashMap<>();

  public EventBridgePartialPublisher(
      final EventBridgeClient client, final String topic, final int partitionCount) {
    this.client = client;
    this.topic = topic;
    this.partitionCount = partitionCount;
  }

  @Override
  public void publish(final Partial partial) {
    // Bridge partitions are 1-indexed (partition ids run 1..partitionCount), so map the hash into
    // that range rather than 0..partitionCount-1.
    final int partition = 1 + Math.floorMod(route(partial.aggId(), partial.key()), partitionCount);
    batches
        .computeIfAbsent(partition, p -> client.newBatch())
        .add(partial.key(), PartialCodec.encode(partial));
  }

  @Override
  public void flush() {
    if (batches.isEmpty()) {
      return;
    }
    final List<CompletableFuture<?>> futures = new ArrayList<>();
    batches.forEach((partition, batch) -> futures.add(batch.publishToTopic(topic, partition)));
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    batches.clear();
  }

  /** Deterministic route so a cell's partials (any writer) always hash to the same partition. */
  private static int route(final int aggId, final byte[] key) {
    return 31 * aggId + Arrays.hashCode(key);
  }
}
