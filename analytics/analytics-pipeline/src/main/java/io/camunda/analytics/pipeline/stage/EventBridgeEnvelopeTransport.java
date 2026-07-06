/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.EventBridgeClient.BatchPublisher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The event-bridge {@link EnvelopeTransport}: buffers shuffle-envelope frames per target facts
 * partition and, on {@link #flush()}, publishes one batch per partition and blocks until durable
 * (produce-before-checkpoint). Routing to a partition is already decided by the caller, so the
 * per-message key is unused.
 */
public final class EventBridgeEnvelopeTransport implements EnvelopeTransport {

  private static final byte[] NO_KEY = new byte[0];

  private final EventBridgeClient client;
  private final String topic;
  private final Map<Integer, BatchPublisher> batches = new HashMap<>();

  public EventBridgeEnvelopeTransport(final EventBridgeClient client, final String topic) {
    this.client = client;
    this.topic = topic;
  }

  @Override
  public void send(final int factsPartition, final byte[] frame) {
    batches.computeIfAbsent(factsPartition, p -> client.newBatch()).add(NO_KEY, frame);
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
}
