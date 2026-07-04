/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.aggregation;

import io.camunda.eventbridge.streaming.aggregate.SegmentDedup;
import io.camunda.eventbridge.streaming.aggregate.SegmentMergingAggregation;
import io.camunda.eventbridge.streaming.processor.Processor;
import io.camunda.eventbridge.streaming.shuffle.CellDelta;
import io.camunda.eventbridge.streaming.shuffle.ShuffleEnvelope;
import io.camunda.eventbridge.streaming.shuffle.sbe.Operation;
import io.camunda.eventbridge.streaming.shuffle.sbe.PayloadKind;
import java.util.List;
import java.util.Map;

/**
 * The Stage-2 reduce node: consumes {@link ShuffleEnvelope}s of sealed segment deltas, dedups each
 * batch by {@link SegmentDedup} (per source partition, so a producer re-emit is dropped wholesale),
 * and dispatches its cell deltas by {@code streamId} to the matching meter's {@link
 * SegmentMergingAggregation}, which merges into one running cell and converges the idempotent
 * serving sink. A terminal node ({@code Out = Void}); {@code flush()} converges the sinks
 * (produce-before-commit) and {@code checkpoint()} persists the merged cells in the runtime's cut.
 *
 * <p>Reference/upsert envelopes are idempotent by key and skip the dedup (handled when the
 * projected/reference path lands).
 */
public final class CubeMergeProcessor implements Processor<ShuffleEnvelope, Void> {

  /** Decodes and merges one cell delta into its meter's running cell. */
  public interface CellApplier {
    void apply(byte[] keyBytes, long windowStart, byte[] accBytes);
  }

  private final SegmentDedup dedup;
  private final Map<Integer, CellApplier> byStreamId;
  private final List<SegmentMergingAggregation<?, ?>> mergers;

  public CubeMergeProcessor(
      final SegmentDedup dedup,
      final Map<Integer, CellApplier> byStreamId,
      final List<SegmentMergingAggregation<?, ?>> mergers) {
    this.dedup = dedup;
    this.byStreamId = Map.copyOf(byStreamId);
    this.mergers = List.copyOf(mergers);
  }

  @Override
  public void process(final ShuffleEnvelope envelope) {
    if (envelope.payloadKind() != PayloadKind.AGGREGATE_DELTA
        || envelope.operation() != Operation.MERGE) {
      return; // reference/upsert records are idempotent by key — no dedup, no merge
    }
    if (!dedup.admit(envelope.producerPartition(), envelope.segment(), envelope.chunk())) {
      return; // a duplicate or producer re-emit of an already-merged batch
    }
    for (final CellDelta cell : envelope.cells()) {
      final CellApplier applier = byStreamId.get(cell.streamId());
      if (applier != null) {
        applier.apply(cell.key(), cell.windowStart(), cell.payload());
      }
    }
  }

  @Override
  public void flush() {
    mergers.forEach(SegmentMergingAggregation::flush);
  }

  @Override
  public void checkpoint() {
    mergers.forEach(SegmentMergingAggregation::checkpoint);
  }

  @Override
  public void close() {
    mergers.forEach(SegmentMergingAggregation::close);
  }
}
