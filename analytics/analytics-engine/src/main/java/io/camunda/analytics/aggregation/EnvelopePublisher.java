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
import io.camunda.eventbridge.streaming.shuffle.ShuffleOperation;
import io.camunda.eventbridge.streaming.shuffle.ShufflePayloadKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

/**
 * Batches a partition's sealed cell deltas into {@link ShuffleEnvelope}s and publishes them via an
 * {@link EnvelopeTransport}. Deltas are buffered per {@code (sourcePartition, segment,
 * factsPartition)} and, on {@link #flush()}, each group becomes one envelope; a per-{@code
 * (sourcePartition, segment)} chunk counter gives every envelope of a segment a monotonic chunk, so
 * the reducer's segment dedup admits each exactly once and skips re-emits. Flush order is
 * deterministic (by segment then facts partition), so a replay reproduces identical chunking. An
 * empty flush is a no-op (the freshness tick emits nothing new).
 *
 * <p><b>Frozen cuts.</b> For a commit cut that persists in the background, publication splits into
 * three steps: {@link #freeze()} encodes the buffered deltas into immutable frames on the owner
 * thread (chunk assignment included — cheap, no transport), {@link #publishFrozen()} sends exactly
 * those frames on the IO thread, and {@link #completeFrozen(boolean)} drops them on success or
 * retains them for the next cut on failure — a re-publish is safe because the reducer dedups by
 * {@code (segment, chunk)}. While a frozen cut is outstanding the IO thread owns the transport, so
 * {@link #flush()} defers (the buffered deltas stay staged for the next flush or freeze). {@link
 * #flush()} remains the synchronous path: it publishes any retained frames of a failed cut first,
 * then the buffer.
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
   * Reused per publisher; encoding happens only where the buffer is owned — a freeze on the actor
   * thread or a synchronous flush on the commit thread — never concurrently.
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

  /** An encoded envelope staged for publication: its target facts partition and wire frame. */
  private record PreparedEnvelope(int factsPartition, byte[] frame) {}

  /**
   * The outstanding frozen cut's frames (null when none): encoded at the freeze barrier, owned by
   * the IO thread between {@link #publishFrozen()} and {@link #completeFrozen(boolean)}.
   */
  private List<PreparedEnvelope> frozenFrames;

  /**
   * A failed cut's frames, re-published ahead of newer output (order preserves the per-stream
   * monotonic {@code (segment, chunk)} sequence the reducer's dedup relies on). Owner thread only.
   */
  private final List<PreparedEnvelope> retryFrames = new ArrayList<>();

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
    if (frozenFrames != null) {
      // An in-flight frozen cut owns the transport on the IO thread. Defer this freshness flush:
      // the buffered deltas stay staged and the next flush or freeze delivers them.
      return;
    }
    if (buffer.isEmpty() && retryFrames.isEmpty()) {
      return;
    }
    for (final PreparedEnvelope prepared : retryFrames) {
      transport.send(prepared.factsPartition(), prepared.frame());
    }
    retryFrames.clear();
    encodeBuffered(prepared -> transport.send(prepared.factsPartition(), prepared.frame()));
    transport.flush();
  }

  /**
   * Owner thread, at the commit barrier: encodes every buffered group into immutable frames —
   * chunks assigned exactly as {@link #flush()} would — prefixed by any retained frames of a failed
   * cut, and stages them as the outstanding frozen cut. Cheap (no transport); folding may resume
   * and buffer new deltas immediately, they belong to the next cut.
   *
   * @throws IllegalStateException if a frozen cut is already outstanding
   */
  public void freeze() {
    if (frozenFrames != null) {
      throw new IllegalStateException(
          "expected no outstanding frozen envelope frames, but freeze() was called again before"
              + " completeFrozen()");
    }
    final List<PreparedEnvelope> frames = new ArrayList<>(retryFrames.size() + buffer.size());
    frames.addAll(retryFrames);
    retryFrames.clear();
    encodeBuffered(frames::add);
    frozenFrames = frames;
  }

  /**
   * IO thread, outside the state transaction: publishes the frozen frames, then flushes the
   * transport durably (produce-before-commit). Touches only the frozen frames and the transport —
   * never the live buffer the owner thread keeps filling. Idempotent under replay: the reducer
   * drops a re-emitted {@code (segment, chunk)}.
   *
   * @throws IllegalStateException if nothing is frozen
   */
  public void publishFrozen() {
    if (frozenFrames == null) {
      throw new IllegalStateException("expected frozen envelope frames to publish, but none");
    }
    if (frozenFrames.isEmpty()) {
      return;
    }
    for (final PreparedEnvelope prepared : frozenFrames) {
      transport.send(prepared.factsPartition(), prepared.frame());
    }
    transport.flush();
  }

  /**
   * Owner thread, once the cut's outcome is known. Success: the frames are durable downstream —
   * drop them. Failure: retain them so the next cut (or the next synchronous {@link #flush()})
   * re-publishes them first; whether or not the failed publish actually delivered, the re-send is
   * dropped by the reducer's {@code (segment, chunk)} dedup.
   *
   * @throws IllegalStateException if nothing is frozen
   */
  public void completeFrozen(final boolean success) {
    if (frozenFrames == null) {
      throw new IllegalStateException("expected frozen envelope frames to complete, but none");
    }
    if (!success) {
      retryFrames.addAll(frozenFrames);
    }
    frozenFrames = null;
  }

  /**
   * Encodes every buffered group in deterministic flush order, assigning each segment's next
   * monotonic chunk, and clears the buffer. Runs only on the thread that owns the buffer.
   */
  private void encodeBuffered(final Consumer<PreparedEnvelope> out) {
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
              ShufflePayloadKind.AGGREGATE_DELTA,
              ShuffleOperation.MERGE,
              entry.getValue());
      out.accept(new PreparedEnvelope(key.factsPartition(), codec.encode(envelope)));
    }
    flushOrder.clear();
    buffer.clear();
    lastKey = null;
    lastGroup = null;
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
