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
 * The reduce-side dedup gate for segment-delta batches: a per-source-partition high-watermark of
 * the last admitted {@link SegmentPosition}. It turns an at-least-once, non-transactional delivery
 * into effectively-once merging without a changelog or transactions — the correctness counterpart
 * to {@link SegmentSealingAggregation}.
 *
 * <p>Because a sealed segment's contribution to a given reduce partition is one atomic batch (its
 * cells merged together, tagged with a monotonic {@code (segment, chunk)} position), a scalar
 * watermark per source partition suffices: {@link #admit} a batch iff its position is strictly
 * newer than the watermark, and skip any at-or-below it — so a re-emitted batch (a producer replay)
 * is dropped wholesale while genuinely new batches pass. The watermark set is tiny (one position
 * per source partition); the caller persists {@link #snapshot()} in its checkpoint and {@link
 * #restore} it on recovery, so dedup survives restarts.
 *
 * <p>Only non-idempotent applications need this gate; idempotent-by-key ones (upsert/delete) can
 * apply every batch directly.
 */
public final class SegmentDedup {

  private final Map<Integer, SegmentPosition> watermark = new HashMap<>();

  /**
   * Admits a batch at {@code (segment, chunk)} from {@code sourcePartition} for merging, advancing
   * the watermark; returns {@code false} (skip) if that position was already admitted — a duplicate
   * or a producer re-emit.
   */
  public boolean admit(final int sourcePartition, final long segment, final int chunk) {
    final SegmentPosition position = new SegmentPosition(segment, chunk);
    final SegmentPosition current = watermark.get(sourcePartition);
    if (current != null && position.compareTo(current) <= 0) {
      return false;
    }
    watermark.put(sourcePartition, position);
    return true;
  }

  /** The last-admitted position per source partition, for the caller to persist at checkpoint. */
  public Map<Integer, SegmentPosition> snapshot() {
    return Map.copyOf(watermark);
  }

  /** Restores the watermarks from a {@link #snapshot()} taken before a restart. */
  public void restore(final Map<Integer, SegmentPosition> snapshot) {
    watermark.clear();
    watermark.putAll(snapshot);
  }
}
