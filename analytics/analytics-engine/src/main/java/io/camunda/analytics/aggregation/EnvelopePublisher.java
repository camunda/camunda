/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.aggregation;

import io.camunda.eventbridge.streaming.shuffle.CellDelta;
import io.camunda.eventbridge.streaming.shuffle.ShuffleEnvelope;
import io.camunda.eventbridge.streaming.shuffle.ShuffleEnvelopeCodec;
import io.camunda.eventbridge.streaming.shuffle.sbe.Operation;
import io.camunda.eventbridge.streaming.shuffle.sbe.PayloadKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Batches a partition's sealed cell deltas into {@link ShuffleEnvelope}s and publishes them via an
 * {@link EnvelopeTransport}. Deltas are buffered per {@code (sourcePartition, segment,
 * factsPartition)} and, on {@link #flush()}, each group becomes one envelope; a per-{@code
 * (sourcePartition, segment)} chunk counter gives every envelope of a segment a monotonic chunk, so
 * the reducer's segment dedup admits each exactly once and skips re-emits. Flush order is
 * deterministic (by segment then facts partition), so a replay reproduces identical chunking. An
 * empty flush is a no-op (the freshness tick emits nothing new).
 */
public final class EnvelopePublisher {

  private final EnvelopeTransport transport;
  private final int schemaVersion;
  private final long producedAt;

  private static final Comparator<Entry<BufferKey, List<CellDelta>>> FLUSH_ORDER =
      Comparator.<Entry<BufferKey, List<CellDelta>>>comparingLong(e -> e.getKey().segment())
          .thenComparingInt(e -> e.getKey().factsPartition());

  private final Map<BufferKey, List<CellDelta>> buffer = new HashMap<>();
  private final Map<SegmentKey, Integer> nextChunk = new HashMap<>();

  /**
   * Reused per publisher; flushes alternate between the actor and commit threads, never overlap.
   */
  private final ShuffleEnvelopeCodec codec = new ShuffleEnvelopeCodec();

  /** Reused flush working list; cleared after each flush. */
  private final List<Entry<BufferKey, List<CellDelta>>> flushOrder = new ArrayList<>();

  // Last-group memo for add(): a seal emits runs of cells for one (sourcePartition, segment), so
  // consecutive cells routed to the same facts partition skip the key allocation and map lookup.
  private BufferKey lastKey;
  private List<CellDelta> lastGroup;

  private record BufferKey(int sourcePartition, long segment, int factsPartition) {}

  private record SegmentKey(int sourcePartition, long segment) {}

  public EnvelopePublisher(
      final EnvelopeTransport transport, final int schemaVersion, final long producedAt) {
    this.transport = transport;
    this.schemaVersion = schemaVersion;
    this.producedAt = producedAt;
  }

  /** Buffers one cell delta for its target facts partition. */
  public void add(
      final int sourcePartition,
      final long segment,
      final int factsPartition,
      final CellDelta cell) {
    if (lastKey == null
        || lastKey.sourcePartition() != sourcePartition
        || lastKey.segment() != segment
        || lastKey.factsPartition() != factsPartition) {
      final BufferKey key = new BufferKey(sourcePartition, segment, factsPartition);
      lastKey = key;
      lastGroup = buffer.computeIfAbsent(key, k -> new ArrayList<>());
    }
    lastGroup.add(cell);
  }

  /** Publishes every buffered group as one envelope (chunked per segment), then flushes durably. */
  public void flush() {
    if (buffer.isEmpty()) {
      return;
    }
    flushOrder.addAll(buffer.entrySet());
    flushOrder.sort(FLUSH_ORDER);
    for (final Entry<BufferKey, List<CellDelta>> entry : flushOrder) {
      final BufferKey key = entry.getKey();
      final SegmentKey segmentKey = new SegmentKey(key.sourcePartition(), key.segment());
      // 0 on first envelope of a segment, then 1, 2, … — a monotonic chunk per segment.
      final int chunk = nextChunk.merge(segmentKey, 0, (current, ignored) -> current + 1);
      final ShuffleEnvelope envelope =
          new ShuffleEnvelope(
              producedAt,
              schemaVersion,
              key.sourcePartition(),
              key.segment(),
              chunk,
              false,
              PayloadKind.AGGREGATE_DELTA,
              Operation.MERGE,
              entry.getValue());
      transport.send(key.factsPartition(), codec.encode(envelope));
    }
    flushOrder.clear();
    buffer.clear();
    lastKey = null;
    lastGroup = null;
    transport.flush();
  }

  /**
   * Drops the chunk counters of every segment strictly below {@code segmentExclusive}, keeping
   * {@link #nextChunk} bounded instead of accreting one entry per segment ever flushed.
   *
   * <p>Safe once no stream can seal a segment below {@code segmentExclusive} anymore — the owning
   * task calls this at its commit, right after it has watermark-sealed every stream up to the
   * committed offset and flushed: from then on every stream's open segment (and any segment a
   * future record can open) is at or above the watermark's segment, so a pruned counter can never
   * be consulted again. The reduce side is unaffected: its {@code SegmentDedup} watermarks are per
   * stream, and a stream seals each segment at most once.
   */
  public void pruneChunkCountersBelow(final long segmentExclusive) {
    nextChunk.keySet().removeIf(key -> key.segment() < segmentExclusive);
  }

  /** The number of segments currently holding a chunk counter (test visibility for the bound). */
  int trackedSegments() {
    return nextChunk.size();
  }
}
