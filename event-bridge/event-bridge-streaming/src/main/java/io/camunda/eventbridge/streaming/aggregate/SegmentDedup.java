/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import java.util.HashMap;
import java.util.Map;

/**
 * The reduce-side dedup gate for segment-delta batches: a high-watermark of the last admitted
 * {@link SegmentPosition} per <em>shuffle stream</em>, keyed by {@code (sourcePartition,
 * streamId)}. It turns an at-least-once, non-transactional delivery into effectively-once merging
 * without a changelog or transactions — the correctness counterpart to {@link
 * SegmentSealingAggregation}.
 *
 * <p>One source partition multiplexes many independent streams (one per meter/{@code aggId}), and
 * each seals its segments on its own cadence — a dense stream races ahead while a sparse one lags,
 * so the same segment index is emitted by different streams in different flushes (different {@code
 * chunk}s). A single scalar watermark per source partition would therefore drop a slow stream's
 * legitimately-new segment as a "re-emit" once a fast stream advanced the watermark past it. So the
 * watermark is kept <em>per stream</em>: each stream seals its own segments in strictly increasing
 * {@code (segment, chunk)} order, so {@link #admit} a batch iff its position is strictly newer than
 * that stream's watermark, and skip any at-or-below — dropping only a genuine re-emit of that
 * stream. The caller persists {@link #snapshot()} in its checkpoint and {@link #restore}s it on
 * recovery, so dedup survives restarts.
 *
 * <p>Only non-idempotent applications need this gate; idempotent-by-key ones (upsert/delete) can
 * apply every batch directly.
 */
public final class SegmentDedup {

  /** A shuffle stream within a source partition: {@code (sourcePartition, streamId)}. */
  public record StreamKey(int sourcePartition, int streamId) {}

  private final Map<StreamKey, SegmentPosition> watermark = new HashMap<>();

  /**
   * Admits a batch at {@code (segment, chunk)} from {@code (sourcePartition, streamId)} for
   * merging, advancing that stream's watermark; returns {@code false} (skip) if that position was
   * already admitted for the stream — a duplicate or a producer re-emit.
   */
  public boolean admit(
      final int sourcePartition, final int streamId, final long segment, final int chunk) {
    final StreamKey key = new StreamKey(sourcePartition, streamId);
    final SegmentPosition position = new SegmentPosition(segment, chunk);
    final SegmentPosition current = watermark.get(key);
    if (current != null && position.compareTo(current) <= 0) {
      return false;
    }
    watermark.put(key, position);
    return true;
  }

  /**
   * The last-admitted position per {@code (sourcePartition, streamId)}, to persist at checkpoint.
   */
  public Map<StreamKey, SegmentPosition> snapshot() {
    return Map.copyOf(watermark);
  }

  /** Restores the watermarks from a {@link #snapshot()} taken before a restart. */
  public void restore(final Map<StreamKey, SegmentPosition> snapshot) {
    watermark.clear();
    watermark.putAll(snapshot);
  }
}
