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
import io.camunda.eventbridge.streaming.sink.FrozenOutbox;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
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
 * {@code (segment, chunk)}. The frame lifecycle (detach, retain, re-emit retained frames ahead of
 * newer output — preserving the per-stream monotonic {@code (segment, chunk)} sequence the
 * reducer's dedup relies on) lives in a {@link FrozenOutbox}; this class keeps the encoding, the
 * chunk counters and the transport. While a frozen cut is outstanding the IO thread owns the
 * transport, so {@link #flush()} defers (the buffered deltas stay staged for the next flush or
 * freeze). {@link #flush()} remains the synchronous path: it publishes any retained frames of a
 * failed cut first, then the buffer.
 *
 * <p><b>Eager mode (opt-in).</b> With {@code eagerPublish} enabled, {@link #publishSealedEagerly()}
 * hands the buffered deltas to the transport the moment their segments seal — encoded, staged and
 * dispatched on the owner thread, non-blocking — so the cut's publish step reduces to awaiting the
 * acknowledgments still outstanding at the barrier instead of bursting every frame there. The
 * barrier partitions the outstanding set exactly: {@link #freeze()} detaches the acknowledgments of
 * everything dispatched before it, and frames sealing afterwards stay buffered for the next cut.
 * Eager dispatches are <em>suppressed</em> while a frozen cut is outstanding (the IO thread owns
 * the transport then — the ownership rule is kept by not sending, rather than by a second handoff)
 * and while a failed cut's retained frames are pending (they must re-emit first to keep the
 * per-stream monotonic sequence). Eagerly-dispatched frames still ride the outbox, so a failed cut
 * retains them for retry like any other frame, and a crash before the cut completes simply
 * re-publishes them on replay — duplicates the reducer's {@code (segment, chunk)} dedup absorbs.
 */
public final class EnvelopePublisher {

  private final EnvelopeTransport transport;
  private final int schemaVersion;
  private final long producedAt;
  private final boolean eagerPublish;

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
   * The prepared frames' cut lifecycle: encoded into it at the freeze barrier, owned by the IO
   * thread between {@link #publishFrozen()} and {@link #completeFrozen(boolean)}, and retained
   * across a failed cut for retry-first re-publication.
   */
  private final FrozenOutbox<PreparedEnvelope> outbox = new FrozenOutbox<>();

  // Eager-mode bookkeeping (owner thread unless noted). The eagerly-dispatched frames are always
  // a prefix of the outbox's staged pile: eager dispatch covers the whole buffer whenever it runs,
  // and it never runs while a failed cut's retained frames are pending — so whenever the prefix is
  // non-empty, the retained pile is empty and the prefix is also the prefix of the frozen pile.
  /** Acknowledgments of eager dispatches since the last freeze. */
  private final List<CompletableFuture<Void>> eagerAcks = new ArrayList<>();

  /** Detached at the freeze barrier: the acknowledgments the outstanding cut must await. */
  private List<CompletableFuture<Void>> frozenEagerAcks = List.of();

  /** How many staged frames were already dispatched eagerly (the staged pile's prefix). */
  private int eagerlySentStaged;

  /** The already-dispatched prefix of the frozen pile, skipped by {@link #publishFrozen()}. */
  private int frozenEagerlySent;

  /** IO-thread scratch: the drain position while {@link #publishFrozen()} skips the prefix. */
  private int drainCursor;

  /**
   * A failed cut's frames are retained for retry-first re-emission; eager dispatch suspends until
   * the next freeze (or synchronous flush) has taken them, so no newer frame can overtake them.
   */
  private boolean retainedPending;

  public EnvelopePublisher(
      final EnvelopeTransport transport, final int schemaVersion, final long producedAt) {
    this(transport, schemaVersion, producedAt, false);
  }

  public EnvelopePublisher(
      final EnvelopeTransport transport,
      final int schemaVersion,
      final long producedAt,
      final boolean eagerPublish) {
    this.transport = transport;
    this.schemaVersion = schemaVersion;
    this.producedAt = producedAt;
    this.eagerPublish = eagerPublish;
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
    if (outbox.hasFrozen()) {
      // An in-flight frozen cut owns the transport on the IO thread. Defer this freshness flush:
      // the buffered deltas stay staged and the next flush or freeze delivers them.
      return;
    }
    if (eagerPublish && !retainedPending) {
      // Eager mode: frames go out the moment their segments seal, so freshness needs no blocking
      // drain here — just nudge anything still buffered on its way; the next cut awaits the acks.
      publishSealedEagerly();
      return;
    }
    if (buffer.isEmpty() && !outbox.hasStaged()) {
      return;
    }
    // Any retained frames of a failed cut go out first, then the freshly encoded buffer. (In eager
    // mode this is also the retry path: while frames are retained no eager frame was dispatched —
    // eager dispatch suspends after a failure — so this drain re-sends them in staging order.)
    outbox.drainPending(prepared -> transport.send(prepared.factsPartition(), prepared.frame()));
    encodeBuffered(prepared -> transport.send(prepared.factsPartition(), prepared.frame()));
    transport.flush();
    retainedPending = false;
  }

  /**
   * Owner thread, eager mode only: hands every buffered delta to the transport right away — encoded
   * with exactly the chunking a flush at this moment would assign, staged into the outbox (so a
   * failed or never-completed cut still retains/replays them), and dispatched without blocking. The
   * acknowledgment joins the set the next cut's {@link #publishFrozen()} awaits.
   *
   * <p>A no-op when eager mode is off, when nothing is buffered, while a frozen cut is outstanding
   * (the IO thread owns the transport — the deltas stay buffered for the next cut), or while a
   * failed cut's retained frames are pending (they must re-emit first, at the next freeze or
   * synchronous flush, to keep the per-stream monotonic sequence).
   */
  public void publishSealedEagerly() {
    if (!eagerPublish || buffer.isEmpty() || outbox.hasFrozen() || retainedPending) {
      return;
    }
    encodeBuffered(
        prepared -> {
          outbox.stage(prepared);
          transport.send(prepared.factsPartition(), prepared.frame());
          eagerlySentStaged++;
        });
    eagerAcks.add(transport.dispatch());
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
    if (outbox.hasFrozen()) {
      throw new IllegalStateException(
          "expected no outstanding frozen envelope frames, but freeze() was called again before"
              + " completeFrozen()");
    }
    encodeBuffered(outbox::stage);
    outbox.freeze();
    // The barrier partitions the eager-mode outstanding set: the cut owns exactly the frames and
    // acknowledgments dispatched before this point (the frozen pile's already-sent prefix and the
    // detached acks); frames sealing from here on buffer for the next cut — eager dispatch stays
    // suppressed while this cut is outstanding. Any retained frames of a failed cut are inside the
    // frozen pile now, so eager dispatch may resume once this cut completes.
    frozenEagerlySent = eagerlySentStaged;
    eagerlySentStaged = 0;
    retainedPending = false;
    if (!eagerAcks.isEmpty()) {
      frozenEagerAcks = new ArrayList<>(eagerAcks);
      eagerAcks.clear();
    }
  }

  /**
   * IO thread, outside the state transaction: publishes the frozen frames pipelined — every frame
   * is handed to the transport up front, then a single await covers all acknowledgments
   * (produce-before-commit), so the wait is the slowest destination's round trip, not the sum.
   * Touches only the frozen frames and the transport — never the live buffer the owner thread keeps
   * filling. Idempotent under replay: the reducer drops a re-emitted {@code (segment, chunk)}.
   *
   * <p>Any failed acknowledgment fails the whole publish, and with it the cut: the outbox retains
   * every frozen frame for the next cut. That retry may re-send frames the failed pipeline had
   * already acknowledged — a duplicate the reducer's {@code (segment, chunk)} dedup absorbs — and
   * re-emits them ahead of newer frames, so the per-stream monotonic sequence is preserved across
   * the failure.
   *
   * @throws IllegalStateException if nothing is frozen
   */
  public void publishFrozen() {
    // In eager mode the frozen pile's prefix was already dispatched before the barrier — send only
    // the remainder; with eager mode off the prefix is empty and every frame is sent here.
    drainCursor = 0;
    final int drained = outbox.drainFrozen(this::sendUnlessDispatchedEagerly);
    final boolean remainderSent = drained > frozenEagerlySent;
    if (frozenEagerAcks.isEmpty()) {
      if (remainderSent) {
        transport.dispatch().join();
      }
      return;
    }
    // One await for the whole cut: every eager acknowledgment outstanding at the barrier plus the
    // remainder's dispatch. Any failure fails the cut here at the latest — an eager send that
    // failed long before the barrier surfaces through its retained acknowledgment.
    final CompletableFuture<?>[] acks =
        new CompletableFuture<?>[frozenEagerAcks.size() + (remainderSent ? 1 : 0)];
    for (int i = 0; i < frozenEagerAcks.size(); i++) {
      acks[i] = frozenEagerAcks.get(i);
    }
    if (remainderSent) {
      acks[acks.length - 1] = transport.dispatch();
    }
    CompletableFuture.allOf(acks).join();
  }

  /** Sends a drained frozen frame unless it belongs to the eagerly-dispatched prefix. */
  private void sendUnlessDispatchedEagerly(final PreparedEnvelope prepared) {
    if (drainCursor++ < frozenEagerlySent) {
      return;
    }
    transport.send(prepared.factsPartition(), prepared.frame());
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
    outbox.completeFrozen(success);
    frozenEagerlySent = 0;
    frozenEagerAcks = List.of();
    if (!success) {
      // The outbox retained the whole frozen pile — the eagerly-dispatched prefix included. The
      // retry re-sends that prefix even though parts of it may already be durable downstream:
      // duplicates the reducer's (segment, chunk) dedup absorbs. Eager dispatch suspends until
      // the retained frames re-emit, so nothing newer can overtake them.
      retainedPending = true;
    }
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
