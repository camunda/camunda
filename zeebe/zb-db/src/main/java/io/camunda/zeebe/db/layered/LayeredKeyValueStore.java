/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

import io.camunda.zeebe.db.layered.segment.FlatSegment;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * A bytes-bounded, log-first layered store over a durable {@link BytesStore} delegate. Writes never
 * reach the delegate on the processing path — they are buffered in memory and drained rarely, in
 * atomic persist rounds (see {@link LayeredStoreCoordinator}), because the log stream is the
 * write-ahead log and a crash replays it. Reads resolve top-down, first hit wins, tombstones hide
 * everything below:
 *
 * <ol>
 *   <li><em>Staging</em> — the current batch's writes, and only those. The rollback unit: {@link
 *       #discard()} clears exactly this layer, {@link #promote()} folds it into the active overlay
 *       once the batch's records reached the log. This preserves today's invariant that an error
 *       discards one batch — never previously committed state.
 *   <li><em>Active overlay</em> — everything promoted since the last {@link #freeze(long)}.
 *       Mutable, owner-thread only. Everything here is already durable in the log.
 *   <li><em>Pipeline</em> — frozen {@link FlatSegment}s, newest first. Immutable; safe to share
 *       with the persist IO thread and reader views. Merged down (newest-wins, with
 *       delete-absorption if enabled) when longer than the segment limit, so read amplification
 *       stays bounded no matter how long persistence is deferred.
 *   <li><em>Clean cache</em> — delegate-backed read-through entries, LRU, the only evictable layer.
 *       Never part of scan merges: its entries are by definition in the delegate, so the delegate
 *       scan already returns them.
 *   <li>The delegate — committed durable state, lagging the log by the persist cadence.
 * </ol>
 *
 * <p><b>Delete absorption</b> (opt-in): a delete of a never-flushed put annihilates the pair in
 * memory — neither write ever reaches the delegate. Sound only under the caller's guarantee that a
 * deleted key is never read again (deletes are garbage collection of dead rows, not semantics): if
 * the delegate holds an older flushed version, skipping the tombstone leaves dead space reclaimed
 * by compaction, never a resurrectable read.
 *
 * <p><b>Byte budget:</b> staging, active and pipeline entries are pinned — evicting or persisting
 * them outside a persist round would put durable state ahead of the recovery anchor. Only clean
 * entries evict, so the budget is soft while buffered writes exist; {@link #overCapacity()} signals
 * the runtime to schedule a persist round now.
 *
 * <p><b>Threading:</b> every method runs on the owner thread. Cross-thread access happens only
 * through immutable segments handed out by {@link #beginPersist()} and {@link
 * #segmentsNewestFirst()} — never through this class's mutable layers.
 */
public final class LayeredKeyValueStore {

  private final String name;

  public LayeredKeyValueStore(
      final String name,
      final BytesStore delegate,
      final long maxBytes,
      final boolean absorbDeletes,
      final int pipelineSegmentLimit) {
    this.name = name;
    throw new UnsupportedOperationException("implemented in task #4");
  }

  /** The store name, used to route persist-batch writes and view reads. */
  public String name() {
    return name;
  }

  // ------------------------------------------------------------------
  // Write path (owner thread) — all writes land in staging, and only there
  // ------------------------------------------------------------------

  public void put(final byte[] key, final byte[] value) {
    throw new UnsupportedOperationException("implemented in task #4");
  }

  public void delete(final byte[] key) {
    throw new UnsupportedOperationException("implemented in task #4");
  }

  /**
   * Folds the staging layer into the active overlay — called when the batch's records were
   * successfully written to the log. A staging entry supersedes an active entry for the same key;
   * delete absorption (if enabled) annihilates a staging delete meeting a never-flushed put.
   */
  public void promote() {
    throw new UnsupportedOperationException("implemented in task #4");
  }

  /**
   * Clears the staging layer — the whole rollback of a failed batch. Active overlay, pipeline and
   * clean cache are untouched: they hold state from previously committed batches, which a failed
   * batch must never affect.
   */
  public void discard() {
    throw new UnsupportedOperationException("implemented in task #4");
  }

  // ------------------------------------------------------------------
  // Read path (owner thread) — staging → active → pipeline → clean → delegate
  // ------------------------------------------------------------------

  /** The value visible to the owner thread, or {@code null} if absent or tombstoned. */
  public byte[] get(final byte[] key) {
    throw new UnsupportedOperationException("implemented in task #4");
  }

  public boolean exists(final byte[] key) {
    throw new UnsupportedOperationException("implemented in task #4");
  }

  /**
   * Visits every visible entry whose key starts with {@code prefix}, in unsigned-byte key order — a
   * k-way merge of staging, active, all pipeline segments and the delegate stream, upper layers
   * shadowing lower ones on equal keys, tombstones hiding everything below.
   */
  public void prefixScan(final byte[] prefix, final BiConsumer<byte[], byte[]> visitor) {
    throw new UnsupportedOperationException("implemented in task #4");
  }

  public void forEach(final BiConsumer<byte[], byte[]> visitor) {
    throw new UnsupportedOperationException("implemented in task #4");
  }

  // ------------------------------------------------------------------
  // Lifecycle (owner thread; driven by the coordinator)
  // ------------------------------------------------------------------

  /**
   * Flattens the active overlay into an immutable {@link FlatSegment} stamped with {@code
   * watermark} (the highest log position whose effects it contains), pushes it onto the pipeline,
   * and installs a fresh active overlay. Pointer swaps and one flatten — no delegate IO. Merges the
   * non-persisting tail of the pipeline down when it exceeds the segment limit. Staging must be
   * empty (no batch in flight). A no-op if the active overlay is empty.
   */
  public void freeze(final long watermark) {
    throw new UnsupportedOperationException("implemented in task #4");
  }

  /**
   * Marks every current pipeline segment as persisting and returns them oldest-first for draining.
   * The segments stay in the pipeline — readable and shadowing the delegate — until {@link
   * #completePersist(boolean)}. Throws {@link IllegalStateException} if a persist is already
   * outstanding (persist rounds are single-flight).
   */
  public List<FlatSegment> beginPersist() {
    throw new UnsupportedOperationException("implemented in task #4");
  }

  /**
   * On success, retires the persisting segments: their live values move to the clean cache
   * (delegate-backed now, evictable), tombstones drop, and the cache trims to budget. On failure,
   * simply un-marks them — they stay in the pipeline and the next round retries them.
   */
  public void completePersist(final boolean success) {
    throw new UnsupportedOperationException("implemented in task #4");
  }

  /**
   * The current pipeline segments, newest first — the immutable share of this store's state for a
   * {@link ReadOnlyView}. Includes segments currently persisting.
   */
  public List<FlatSegment> segmentsNewestFirst() {
    throw new UnsupportedOperationException("implemented in task #4");
  }

  // ------------------------------------------------------------------
  // Accounting
  // ------------------------------------------------------------------

  /** Approximate heap footprint of staging + active + pipeline + clean together. */
  public long approximateBytes() {
    throw new UnsupportedOperationException("implemented in task #4");
  }

  /**
   * Whether pinned (un-evictable) entries hold the store over its byte budget — the signal to run a
   * persist round now.
   */
  public boolean overCapacity() {
    throw new UnsupportedOperationException("implemented in task #4");
  }
}
