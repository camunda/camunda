/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import io.camunda.analytics.aggregation.EnvelopeTransport;
import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.EventBridgeClient.BatchPublisher;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

/**
 * The event-bridge {@link EnvelopeTransport}: buffers shuffle-envelope frames per target facts
 * partition and, on {@link #dispatch()}, publishes one batch per partition — all partitions
 * pipelined at once, acknowledged through the returned future. Routing to a partition is already
 * decided by the caller, so the per-message key is unused.
 *
 * <p><b>How the ordering contract is met.</b> The underlying client gives no ordering across
 * requests that are in flight concurrently (publishes are independent async HTTP requests over a
 * shared connection pool), so per-destination order comes from two mechanisms instead: every frame
 * sent between two dispatches joins one batch per partition — a single publish request whose
 * entries the broker appends in batch order — and successive dispatches to the same partition are
 * chained, each starting only after the previous one completed. Distinct partitions stay
 * independent and publish concurrently.
 *
 * <p><b>Threading.</b> Single writer, no locks: the publisher's owner thread and the frozen cut's
 * IO thread touch this transport alternately, never concurrently — the freeze/complete handoff of
 * the cut protocol provides the happens-before edges, exactly as for the publisher's outbox.
 */
public final class EventBridgeEnvelopeTransport implements EnvelopeTransport {

  private static final byte[] NO_KEY = new byte[0];

  private final EventBridgeClient client;
  private final String topic;
  private final Map<Integer, BatchPublisher> batches = new HashMap<>();

  /**
   * The last initiated publish per facts partition; a later dispatch to the same partition chains
   * behind it so two in-flight publishes can never arrive reordered. Bounded by the facts partition
   * count.
   */
  private final Map<Integer, CompletableFuture<?>> tails = new HashMap<>();

  public EventBridgeEnvelopeTransport(final EventBridgeClient client, final String topic) {
    this.client = client;
    this.topic = topic;
  }

  @Override
  public void send(final int factsPartition, final byte[] frame) {
    batches.computeIfAbsent(factsPartition, p -> client.newBatch()).add(NO_KEY, frame);
  }

  @Override
  public CompletableFuture<Void> dispatch() {
    if (batches.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    final CompletableFuture<?>[] acks = new CompletableFuture<?>[batches.size()];
    int index = 0;
    for (final Entry<Integer, BatchPublisher> entry : batches.entrySet()) {
      acks[index++] = publishInOrder(entry.getKey(), entry.getValue());
    }
    // The batches are handed to the client here; retry ownership stays with the caller's outbox,
    // which retains the frames of a failed cut and re-sends them as fresh batches.
    batches.clear();
    return CompletableFuture.allOf(acks);
  }

  /**
   * Publishes {@code batch} to {@code partition} after the partition's previous publish (if any)
   * completed. The chain deliberately continues past a failed predecessor — its failure already
   * surfaced through its own dispatch future and failed that cut; whether or not it actually
   * appended, the frames that follow are either fresh or a retained-frame retry whose duplicates
   * the reducer's {@code (segment, chunk)} dedup absorbs.
   */
  private CompletableFuture<?> publishInOrder(final int partition, final BatchPublisher batch) {
    final CompletableFuture<?> previous = tails.get(partition);
    final CompletableFuture<?> ack =
        previous == null
            ? batch.publishToTopic(topic, partition)
            : previous
                .handle((ignoredResult, ignoredError) -> null)
                .thenCompose(ignored -> batch.publishToTopic(topic, partition));
    tails.put(partition, ack);
    return ack;
  }
}
