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
import io.camunda.eventbridge.streaming.shuffle.ShuffleOperation;
import io.camunda.eventbridge.streaming.shuffle.ShufflePayloadKind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Stage-2 reduce node: consumes {@link ShuffleEnvelope}s of sealed segment deltas, dedups each
 * cell by {@link SegmentDedup} per {@code (sourcePartition, streamId)}, and dispatches its cell
 * deltas by {@code streamId} to the matching meter's {@link SegmentMergingAggregation}, which
 * merges into one running cell and converges the idempotent serving sink. A terminal node ({@code
 * Out = Void}); {@code flush()} converges the sinks (produce-before-commit), and the owning task
 * freezes and persists the mergers' cells inside its commit cut.
 *
 * <p>Dedup is <em>per stream</em>, not per envelope: one source partition multiplexes many streams
 * (one per meter) that seal the same segment in different flushes, so admission is decided once per
 * {@code streamId} in the envelope (all of a stream's cells share the envelope's {@code (segment,
 * chunk)}). A shared per-partition watermark would drop a slow stream's late segment once a fast
 * stream advanced past it.
 *
 * <p>Reference/upsert envelopes are idempotent by key and skip the dedup (handled when the
 * projected/reference path lands).
 */
public final class CubeMergeProcessor implements Processor<ShuffleEnvelope, Void> {

  /**
   * Decodes and merges one cell delta into its meter's running cell. {@code sourcePartition} is the
   * Stage-1 producer partition the delta came from — the source identity the mergers'
   * min-of-sources stream-time clock is keyed by (a fast source must not close a slow source's
   * still-in-flight windows).
   */
  public interface CellApplier {
    void apply(int sourcePartition, byte[] keyBytes, long windowStart, byte[] accBytes);
  }

  /**
   * Receives a delta whose stream has no applier in the current topology — e.g. a newly-declared
   * cube whose deltas arrive before this task's catalog reload wires its merger. The owner parks it
   * durably and drains it (through the dedup) once a reload installs the stream, or discards it
   * when the reloaded catalog does not know the stream.
   */
  public interface DeltaParker {
    void park(int sourcePartition, long segment, int chunk, CellDelta cell);
  }

  private final SegmentDedup dedup;
  private final Map<Integer, CellApplier> byStreamId;
  private final List<SegmentMergingAggregation<?, ?>> mergers;
  private final DeltaParker parker;

  /**
   * Reused per-envelope admit-once-per-stream memo (the task processes envelopes one at a time on
   * its actor thread), cleared at the start of each envelope.
   */
  private final Map<Integer, Boolean> admitted = new HashMap<>();

  public CubeMergeProcessor(
      final SegmentDedup dedup,
      final Map<Integer, CellApplier> byStreamId,
      final List<SegmentMergingAggregation<?, ?>> mergers,
      final DeltaParker parker) {
    this.dedup = dedup;
    this.byStreamId = Map.copyOf(byStreamId);
    this.mergers = List.copyOf(mergers);
    this.parker = parker;
  }

  @Override
  public void process(final ShuffleEnvelope envelope) {
    if (envelope.payloadKind() != ShufflePayloadKind.AGGREGATE_DELTA
        || envelope.operation() != ShuffleOperation.MERGE) {
      return; // reference/upsert records are idempotent by key — no dedup, no merge
    }
    final int sourcePartition = envelope.producerPartition();
    final long segment = envelope.segment();
    final int chunk = envelope.chunk();
    // Admit once per stream in this envelope (all of a stream's cells share its (segment, chunk));
    // a re-emit of that stream's batch is skipped, while a sibling stream's late segment is kept.
    admitted.clear();
    for (final CellDelta cell : envelope.cells()) {
      final CellApplier applier = byStreamId.get(cell.streamId());
      if (applier == null) {
        // Unknown stream — e.g. a cube declared after this task's last catalog reload. Do NOT
        // advance the dedup watermark (admitting without applying would permanently mark these
        // (segment, chunk)s as merged); park the delta instead. The owner drains it through the
        // dedup once a reload installs the stream's applier, or discards it when the reloaded
        // catalog does not know the stream — so a new cube's first deltas survive the reload gap
        // even though the facts offset advances past this envelope.
        parker.park(sourcePartition, segment, chunk, cell);
        continue;
      }
      final boolean merge =
          admitted.computeIfAbsent(
              cell.streamId(), streamId -> dedup.admit(sourcePartition, streamId, segment, chunk));
      if (!merge) {
        continue; // this stream already merged this batch — a duplicate or producer re-emit
      }
      applier.apply(sourcePartition, cell.key(), cell.windowStart(), cell.payload());
    }
  }

  @Override
  public void flush() {
    mergers.forEach(SegmentMergingAggregation::flush);
  }

  @Override
  public void close() {
    mergers.forEach(SegmentMergingAggregation::close);
  }
}
