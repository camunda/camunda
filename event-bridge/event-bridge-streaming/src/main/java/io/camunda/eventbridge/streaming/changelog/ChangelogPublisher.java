/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.changelog;

import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.EventBridgeClient.BatchPublisher;
import java.util.List;

/**
 * Publishes one cut's changelog output (ADR 0009 Decisions 1/2): the frozen delta's puts and
 * tombstones, then the offset marker strictly last, as one {@link BatchPublisher#keyed() keyed}
 * batch to the shard's changelog partition — synchronously (blocking for the broker's ack), so a
 * task's {@link io.camunda.eventbridge.streaming.CommitCut#publish()} can call this and have the
 * changelog durable (or the failure surfaced) before it returns, which is exactly what the cut
 * protocol needs: {@code publish()} always runs to completion before {@code persist()} — see {@link
 * io.camunda.eventbridge.streaming.internals.PartitionCommitter#persistCut}. A publish failure
 * therefore propagates before any local transaction runs, and the cut fails as a whole.
 *
 * <p>The marker is written on <b>every</b> call, even when {@code records} is empty: it is the
 * failover/rebuild resume token (ADR 0009 Decisions 5/6), and a cut can legitimately carry no state
 * change at all (e.g. a filtered-only stretch that still advances the source offset) — a long-idle
 * shard whose most recent cuts changed nothing must still be resumable from its last marker.
 *
 * <p>Partition index equals the source partition index — one changelog partition per shard,
 * matching {@code Task#freezeCut}'s own partition. The changelog topic itself is provisioned once,
 * out of band (see {@link ChangelogTopics}).
 */
public final class ChangelogPublisher {

  private final EventBridgeClient client;
  private final String topic;
  private final int partitionIndex;

  public ChangelogPublisher(
      final EventBridgeClient client, final String topic, final int partitionIndex) {
    this.client = client;
    this.topic = topic;
    this.partitionIndex = partitionIndex;
  }

  /**
   * Publishes {@code records} (in any order) followed by the marker carrying {@code sourceOffset},
   * as one keyed batch. Blocks for the broker's ack.
   *
   * @return the marker's broker-assigned position — the changelog position {@code P} the cut's
   *     local transaction persists alongside its state delta and source offset
   */
  public long publish(final List<ChangelogRecord> records, final long sourceOffset) {
    final BatchPublisher batch = client.newBatch().keyed();
    for (final ChangelogRecord record : records) {
      batch.add(record.key(), record.value());
    }
    // Strictly the last record of the batch: the broker assigns positions in the batch's write
    // order, so the marker's position is exactly the batch's last position (see below).
    batch.add(ChangelogMarker.KEY, ChangelogMarker.encodeValue(sourceOffset));
    final List<Long> positions = batch.publishToTopic(topic, partitionIndex).join();
    // BatchPublisher#publishToTopic returns [firstPosition, lastPosition] for the whole batch; the
    // marker was added last, so the last position is exactly its assigned position.
    return positions.get(positions.size() - 1);
  }
}
