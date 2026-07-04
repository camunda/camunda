/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

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

  private final Map<BufferKey, List<CellDelta>> buffer = new HashMap<>();
  private final Map<SegmentKey, Integer> nextChunk = new HashMap<>();

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
    buffer
        .computeIfAbsent(
            new BufferKey(sourcePartition, segment, factsPartition), k -> new ArrayList<>())
        .add(cell);
  }

  /** Publishes every buffered group as one envelope (chunked per segment), then flushes durably. */
  public void flush() {
    if (buffer.isEmpty()) {
      return;
    }
    buffer.entrySet().stream()
        .sorted(
            Comparator.<Map.Entry<BufferKey, List<CellDelta>>>comparingLong(
                    e -> e.getKey().segment())
                .thenComparingInt(e -> e.getKey().factsPartition()))
        .forEach(
            entry -> {
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
              transport.send(key.factsPartition(), ShuffleEnvelopeCodec.encode(envelope));
            });
    buffer.clear();
    transport.flush();
  }
}
