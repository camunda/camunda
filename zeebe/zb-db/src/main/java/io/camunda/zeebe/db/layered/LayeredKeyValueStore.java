/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

import io.camunda.zeebe.db.layered.segment.ChunkPool;
import io.camunda.zeebe.db.layered.segment.ChunkWriter;
import io.camunda.zeebe.db.layered.segment.FlatSegment;
import io.camunda.zeebe.db.layered.segment.KWayMergeIterator;
import io.camunda.zeebe.db.layered.segment.ShadowingZipper;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
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
 *       with the persist IO thread and reader views. Freezing copies the entry bytes into pooled
 *       chunks (see {@link FlatSegment} for the lifetime rule: the pipeline retains its segments,
 *       and dropping or merging away a segment releases it, recycling chunks no holder references
 *       anymore). Merged down (newest-wins, with delete-absorption if enabled) by the lifecycle
 *       driver when longer than the segment limit — {@link #mergeNeeded()} signals it, {@link
 *       #beginMerge()} captures the run so the merge itself may execute off the owner thread
 *       (index-only: refs move, bytes don't), {@link #completeMerge(FlatSegment, boolean)} swaps
 *       the result in — so read amplification stays bounded no matter how long persistence is
 *       deferred. Merging is adaptive: while the last merge annihilated too few entries (no key
 *       overlap to deduplicate, no pairs to absorb), over-limit merges are skipped and the pipeline
 *       may overshoot to a hard cap of {@link #MERGE_OVERSHOOT_FACTOR} × the limit, where a merge
 *       is forced and the ratio re-measured — so read amplification stays bounded either way.
 *   <li><em>Clean cache</em> — delegate-mirroring entries, LRU, the only evictable layer: values
 *       read through the delegate, plus known-absent sentinels for keys the delegate missed
 *       (negative caching, see {@link #NEGATIVE}). Repopulation after a persist round is lazy —
 *       dropped segments are not copied here; the keys actually read again re-enter via normal
 *       read-through. Never part of scan merges: positive entries are by definition in the
 *       delegate, so the delegate scan already returns them, and negative entries have nothing to
 *       emit.
 *   <li>The delegate — committed durable state, lagging the log by the persist cadence.
 * </ol>
 *
 * <p><b>Delete absorption</b> (opt-in): a delete of a never-flushed put annihilates the pair in
 * memory — neither write ever reaches the delegate. Flushed flags are exact at write time (a write
 * of a key unknown to every in-memory layer falls through to a delegate point read), so a pair only
 * annihilates when the delegate provably never held the key; no behavioral contract is required of
 * callers, and scans can never resurrect an absorbed key.
 *
 * <p><b>Byte budget:</b> staging, active and pipeline entries are pinned — evicting or persisting
 * them outside a persist round would put durable state ahead of the recovery anchor. Only clean
 * entries evict, so the budget is soft while buffered writes exist; {@link #overCapacity()} signals
 * the runtime to schedule a persist round now. Accounted bytes are live entry bytes: chunk-level
 * memory can transiently exceed them (open-chunk tails, and bytes of merged-away versions pinned
 * until their chunks recycle), bounded by the persist cadence that retires whole chunks.
 *
 * <p><b>Threading:</b> every method runs on the owner thread, with one read-only exception: the
 * stat accessors ({@code *Bytes()}, {@code *EntryCount()}, {@link #pipelineDepth()}, {@link
 * #newestSegmentWatermark()}, {@link #approximateBytes()}) may additionally be polled by a metrics
 * scrape thread. They read only {@code volatile} mirrors the owner updates at its mutation points —
 * a scrape sees tear-free point-in-time values and never walks the owner-mutable structures.
 * Cross-thread access to the data itself happens only through immutable segments handed out by
 * {@link #beginPersist()} and {@link #segmentsNewestFirst()} — never through this class's mutable
 * layers.
 */
public final class LayeredKeyValueStore {

  /**
   * Minimum fraction of entries a pipeline merge must annihilate (deduplicated versions plus
   * absorbed put/delete pairs, measured as entries-in vs entries-out) for over-limit merges to keep
   * running eagerly. Below this, merging is mostly copying arrays for no byte savings, so merges
   * are skipped until the pipeline reaches the hard cap.
   */
  private static final double MERGE_ANNIHILATION_THRESHOLD = 0.1;

  /**
   * Hard cap on pipeline overshoot while merges are skipped, as a multiple of the segment limit. A
   * merge at the cap is unconditional and re-measures the annihilation ratio, so a workload shift
   * back to overlapping keys re-enables eager merging.
   */
  private static final int MERGE_OVERSHOOT_FACTOR = 2;

  private static final byte[] EMPTY_PREFIX = new byte[0];

  /**
   * Clean-cache sentinel marking a key the delegate provably does not hold (negative cache),
   * compared by identity — a genuine zero-length delegate value is a different array instance.
   * Negative caching is sound because the delegate content for this store's column family changes
   * only through this store's own persist rounds (single owner per column family, enforced by the
   * domain registry), and every write to a key removes its clean entry first — there is no external
   * invalidation path that could make a cached negative stale. Sentinels are accounted at key bytes
   * only (the shared sentinel array is empty), and they are LRU-evictable exactly like positive
   * entries — eviction merely costs a future re-probe. They can never leak into scans: the clean
   * cache is not part of scan merges.
   */
  private static final byte[] NEGATIVE = new byte[0];

  private final String name;
  private final BytesStore delegate;
  private final long maxBytes;
  private final boolean absorbDeletes;
  private final int pipelineSegmentLimit;
  private final LayeredStoreMetrics metrics;
  private final ChunkWriter chunkWriter;

  // staging and active each pair a hash index (point lookups) with a sorted index (scans); the
  // two indexes of a layer share the same Entry objects
  private final Map<ByteBuffer, Entry> stagingByKey = new HashMap<>();
  private final TreeMap<byte[], Entry> stagingSorted = new TreeMap<>(Arrays::compareUnsigned);
  private final Map<ByteBuffer, Entry> activeByKey = new HashMap<>();
  private final TreeMap<byte[], Entry> activeSorted = new TreeMap<>(Arrays::compareUnsigned);

  // newest first; the segments of an outstanding persist round are the oldest ones — the last
  // persistingCount elements
  private final List<FlatSegment> pipeline = new ArrayList<>();
  private boolean persisting;
  private int persistingCount;

  // the run captured by an outstanding merge (newest first), or null; disjoint from the
  // persisting tail by construction — see beginMerge
  private List<FlatSegment> merging;

  // fraction of entries the last pipeline merge annihilated; starts optimistic (1.0) so the first
  // over-limit merge always runs and measures the real ratio (owner thread only)
  private double lastMergeAnnihilation = 1.0;

  private final LinkedHashMap<ByteBuffer, byte[]> clean = new LinkedHashMap<>(16, 0.75f, true);

  // volatile so a metrics scrape thread reads tear-free values (single writer: the owner thread);
  // the entry-count/pipeline mirrors below exist for the same reason — gauges must never walk the
  // owner-mutable maps or the pipeline list (see the Threading javadoc)
  private volatile long stagingBytes;
  private volatile long activeBytes;
  private volatile long pipelineBytes;
  private volatile long cleanBytes;
  private volatile long stagingEntries;
  private volatile long activeEntries;
  private volatile long pipelineEntries;
  private volatile long cleanEntries;
  private volatile int pipelineSegments;
  private volatile long newestWatermark = -1;

  public LayeredKeyValueStore(
      final String name,
      final BytesStore delegate,
      final long maxBytes,
      final boolean absorbDeletes,
      final int pipelineSegmentLimit) {
    this(name, delegate, maxBytes, absorbDeletes, pipelineSegmentLimit, LayeredStoreMetrics.noop());
  }

  public LayeredKeyValueStore(
      final String name,
      final BytesStore delegate,
      final long maxBytes,
      final boolean absorbDeletes,
      final int pipelineSegmentLimit,
      final LayeredStoreMetrics metrics) {
    this(
        name,
        delegate,
        maxBytes,
        absorbDeletes,
        pipelineSegmentLimit,
        metrics,
        new ChunkWriter(new ChunkPool()));
  }

  /**
   * @param chunkWriter the bump allocator freezes copy entry bytes through; share one writer (and
   *     thus one chunk pool) across all stores of an owner thread so small segments pack into
   *     common chunks — the convenience constructors give each store a private writer instead
   */
  public LayeredKeyValueStore(
      final String name,
      final BytesStore delegate,
      final long maxBytes,
      final boolean absorbDeletes,
      final int pipelineSegmentLimit,
      final LayeredStoreMetrics metrics,
      final ChunkWriter chunkWriter) {
    this.chunkWriter = Objects.requireNonNull(chunkWriter, "chunkWriter");
    this.name = Objects.requireNonNull(name, "name");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    if (maxBytes <= 0) {
      throw new IllegalArgumentException("expected maxBytes to be positive, but was " + maxBytes);
    }
    if (pipelineSegmentLimit < 1) {
      throw new IllegalArgumentException(
          "expected pipelineSegmentLimit to be at least 1, but was " + pipelineSegmentLimit);
    }
    this.maxBytes = maxBytes;
    this.absorbDeletes = absorbDeletes;
    this.pipelineSegmentLimit = pipelineSegmentLimit;
  }

  /** The store name, used to route persist-batch writes and view reads. */
  public String name() {
    return name;
  }

  /** Whether pipeline merges of this store absorb annihilated put/delete pairs. */
  boolean absorbsDeletes() {
    return absorbDeletes;
  }

  // ------------------------------------------------------------------
  // Write path (owner thread) — all writes land in staging, and only there
  // ------------------------------------------------------------------

  public void put(final byte[] key, final byte[] value) {
    write(key, Objects.requireNonNull(value, "value"));
  }

  public void delete(final byte[] key) {
    write(key, null);
  }

  private void write(final byte[] key, final byte[] value) {
    Objects.requireNonNull(key, "key");
    final ByteBuffer mapKey = ByteBuffer.wrap(key);
    // a clean entry mirrors the delegate: the new version shadows it, so remove it to keep the
    // layers disjoint — and it settles the flushed flag exactly, positive or negative
    final byte[] cleanHit = clean.remove(mapKey);
    if (cleanHit != null) {
      cleanBytes -= key.length + cleanHit.length;
      cleanEntries = clean.size();
    }
    final Entry replacedStaging = stagingByKey.get(mapKey);
    // a negative clean hit proves the delegate does not hold the key (see NEGATIVE): flushed is
    // exactly false and the delegate probe is skipped; a positive hit proves it does (flushed=true)
    final boolean flushed =
        cleanHit != null
            ? cleanHit != NEGATIVE
            : (replacedStaging != null && replacedStaging.flushed())
                || flushedBelowStaging(mapKey, key);
    final Entry entry = new Entry(key, value, flushed);
    stagingByKey.put(mapKey, entry);
    stagingSorted.put(key, entry);
    stagingBytes += entrySize(entry) - (replacedStaging == null ? 0 : entrySize(replacedStaging));
    stagingEntries = stagingByKey.size();
    evictIfNeeded();
  }

  /**
   * Whether the delegate holds — or, via the active overlay or the pipeline, will hold — a version
   * of {@code key}, ignoring the staging layer. The newest version below staging decides: a live
   * put will reach the delegate (frozen segments are drained eventually), while a tombstone shadows
   * every older version so nothing below it will. A key found in no in-memory layer falls through
   * to a delegate point read: without it, a blind delete of a delegate-only key would carry {@code
   * flushed=false} and the persist drain would skip its tombstone, resurrecting the durable row.
   * The read makes every flushed flag exact at write time, which in turn makes delete absorption
   * sound without any read-before-delete contract on callers; in practice the layers above absorb
   * the cost, since keys are typically read (and clean-cached) before being mutated.
   */
  private boolean flushedBelowStaging(final ByteBuffer mapKey, final byte[] key) {
    final Entry activeEntry = activeByKey.get(mapKey);
    if (activeEntry != null) {
      // a never-flushed active put does not count: it may still annihilate with a later delete
      return activeEntry.flushed();
    }
    for (final FlatSegment segment : pipeline) {
      final int index = segment.indexOfKey(key);
      if (index >= 0) {
        return !segment.tombstoneAt(index) || segment.flushedAt(index);
      }
    }
    metrics.countFlushedPointRead();
    return delegate.get(key) != null;
  }

  /**
   * Folds the staging layer into the active overlay — called when the batch's records were
   * successfully written to the log. A staging entry supersedes an active entry for the same key;
   * delete absorption (if enabled) annihilates a staging delete meeting a never-flushed put.
   */
  public void promote() {
    for (final Entry entry : stagingSorted.values()) {
      final ByteBuffer mapKey = ByteBuffer.wrap(entry.key());
      final Entry existing = activeByKey.get(mapKey);
      if (absorbDeletes
          && entry.tombstone()
          && !entry.flushed()
          && (existing == null || !existing.flushed())) {
        // no version of this key was ever flushed or will reach the delegate (a non-flushed
        // tombstone guarantees that), so the tombstone — and the never-flushed put it meets, if
        // any — annihilates in memory
        if (existing != null) {
          activeByKey.remove(mapKey);
          activeSorted.remove(entry.key());
          activeBytes -= entrySize(existing);
        }
        metrics.countAnnihilatedWrites(existing != null ? 2 : 1);
        continue;
      }
      // the flushed property is sticky: once any version of the key was flushed, every newer
      // version must reach the delegate too
      final Entry promoted =
          existing != null && existing.flushed() && !entry.flushed()
              ? new Entry(entry.key(), entry.value(), true)
              : entry;
      activeByKey.put(mapKey, promoted);
      activeSorted.put(promoted.key(), promoted);
      activeBytes += entrySize(promoted) - (existing == null ? 0 : entrySize(existing));
    }
    activeEntries = activeByKey.size();
    stagingByKey.clear();
    stagingSorted.clear();
    stagingBytes = 0;
    stagingEntries = 0;
  }

  /**
   * Clears the staging layer — the whole rollback of a failed batch. Active overlay, pipeline and
   * clean cache are untouched: they hold state from previously committed batches, which a failed
   * batch must never affect.
   */
  public void discard() {
    stagingByKey.clear();
    stagingSorted.clear();
    stagingBytes = 0;
    stagingEntries = 0;
  }

  // ------------------------------------------------------------------
  // Read path (owner thread) — staging → active → pipeline → clean → delegate
  // ------------------------------------------------------------------

  /** The value visible to the owner thread, or {@code null} if absent or tombstoned. */
  public byte[] get(final byte[] key) {
    final ByteBuffer mapKey = ByteBuffer.wrap(key);
    Entry entry = stagingByKey.get(mapKey);
    if (entry == null) {
      entry = activeByKey.get(mapKey);
    }
    if (entry != null) {
      return entry.value(); // null for a tombstone — which must not fall through
    }
    for (final FlatSegment segment : pipeline) {
      final int index = segment.indexOfKey(key);
      if (index >= 0) {
        return segment.valueAt(index); // null for a tombstone — which must not fall through
      }
    }
    final byte[] cached = clean.get(mapKey);
    if (cached != null) {
      metrics.countCleanCacheHit();
      return cached == NEGATIVE ? null : cached;
    }
    metrics.countDelegateReadThrough();
    final byte[] committed = delegate.get(key);
    // a miss is cached too (negative caching, sound per NEGATIVE): repeated reads of an absent key
    // and the flushed check of a later write to it are served without another delegate probe
    clean.put(mapKey, committed == null ? NEGATIVE : committed);
    cleanBytes += key.length + (committed == null ? 0 : committed.length);
    cleanEntries = clean.size();
    evictIfNeeded();
    return committed;
  }

  public boolean exists(final byte[] key) {
    return get(key) != null;
  }

  /**
   * Visits every visible entry whose key starts with {@code prefix}, in unsigned-byte key order — a
   * k-way merge of staging, active, all pipeline segments and the delegate stream, upper layers
   * shadowing lower ones on equal keys, tombstones hiding everything below.
   *
   * <p><b>Point-in-time:</b> the scan iterates the state as of the call, so the visitor may freely
   * write to this store (the engine's delete-while-scanning pattern): the staging and active prefix
   * selections are copied up front (cost proportional to the selection, not the store), pipeline
   * segments are immutable, and visitor writes never reach the delegate mid-scan (they land in
   * staging). Consequently a key put during the scan is not visited by it, a key deleted during the
   * scan may still be visited (it was visible at scan start), and every mutation is visible to
   * point reads during and any read or scan after the scan. This is deliberately stricter than the
   * RocksDB-backed reference, whose write-batch delta iterator is live: there, an in-scan write
   * ahead of the cursor does surface in the ongoing scan, and updating the currently visited key is
   * documented as unsafe. Point-in-time is the safer contract, and every visitor-mutation pattern
   * tolerated there is well-defined here.
   */
  public void prefixScan(final byte[] prefix, final BiConsumer<byte[], byte[]> visitor) {
    final List<Iterator<Entry>> layers = new ArrayList<>(2 + pipeline.size());
    layers.add(List.copyOf(prefixSelection(stagingSorted, prefix).values()).iterator());
    layers.add(List.copyOf(prefixSelection(activeSorted, prefix).values()).iterator());
    for (final FlatSegment segment : pipeline) {
      layers.add(segment.range(prefix));
    }
    // the clean cache is intentionally absent: its entries mirror the delegate, which the
    // delegate stream below already returns
    ShadowingZipper.merge(
        new KWayMergeIterator(layers), scan -> delegate.prefixScan(prefix, scan), visitor);
  }

  public void forEach(final BiConsumer<byte[], byte[]> visitor) {
    prefixScan(EMPTY_PREFIX, visitor);
  }

  // ------------------------------------------------------------------
  // Lifecycle (owner thread; driven by the coordinator)
  // ------------------------------------------------------------------

  /**
   * Flattens the active overlay into an immutable {@link FlatSegment} stamped with {@code
   * watermark} (the highest log position whose effects it contains), pushes it onto the pipeline,
   * and installs a fresh active overlay. Pointer swaps and one flatten — no delegate IO, and no
   * merging: the lifecycle driver checks {@link #mergeNeeded()} after freezes and runs the merge
   * (possibly off the owner thread). Staging must be empty (no batch in flight). A no-op if the
   * active overlay is empty.
   */
  public void freeze(final long watermark) {
    if (!stagingByKey.isEmpty()) {
      throw new IllegalStateException(
          "expected staging of store '"
              + name
              + "' to be empty on freeze, but a batch with "
              + stagingByKey.size()
              + " write(s) is in flight");
    }
    if (activeByKey.isEmpty()) {
      return;
    }
    final FlatSegment segment = FlatSegment.of(activeSorted, watermark, chunkWriter);
    pipeline.add(0, segment);
    pipelineBytes += segment.byteSize();
    activeByKey.clear();
    activeSorted.clear();
    activeBytes = 0;
    activeEntries = 0;
    refreshPipelineStats();
  }

  /**
   * Whether the lifecycle driver should run a pipeline merge now: no merge is outstanding, the
   * non-persisting run exceeds the segment limit (persisting segments are pinned by their round —
   * only the newest, non-persisting run ever merges), and merging is expected to pay off. The
   * payoff heuristic is adaptive: while the last merge annihilated too little, an over-limit merge
   * would mostly copy index arrays for no savings, so the merge is declined (and counted as
   * skipped) until the pipeline reaches the hard overshoot cap, where it is unconditional and
   * re-measures the ratio.
   */
  public boolean mergeNeeded() {
    if (merging != null) {
      return false;
    }
    final int nonPersisting = pipeline.size() - persistingCount;
    if (nonPersisting <= pipelineSegmentLimit) {
      return false;
    }
    if (nonPersisting < pipelineSegmentLimit * MERGE_OVERSHOOT_FACTOR
        && lastMergeAnnihilation < MERGE_ANNIHILATION_THRESHOLD) {
      metrics.countPipelineMergeSkipped();
      return false;
    }
    return true;
  }

  /**
   * Captures the current non-persisting pipeline run for merging and returns it oldest-first (the
   * input order of {@link FlatSegment#merge(List, boolean)}). The captured segments stay in the
   * pipeline — retained, readable and shadowing the delegate — until {@link
   * #completeMerge(FlatSegment, boolean)} swaps the merged result in, so the merge itself may
   * execute off the owner thread over the immutable segments. Merges are single-flight per store,
   * and mutually exclusive with persist rounds in one direction: a merge may start while a round is
   * outstanding (the captured run excludes the round's persisting tail by construction), but {@link
   * #beginPersist()} refuses to start while a merge is outstanding — a round captures every
   * pipeline segment, and segments captured by a round must never concurrently merge.
   *
   * @throws IllegalStateException if a merge is already outstanding, or the non-persisting run has
   *     fewer than two segments (nothing to merge — gate on {@link #mergeNeeded()})
   */
  public List<FlatSegment> beginMerge() {
    if (merging != null) {
      throw new IllegalStateException(
          "expected no outstanding merge for store '"
              + name
              + "', but one is in flight (merges are single-flight)");
    }
    final int nonPersisting = pipeline.size() - persistingCount;
    if (nonPersisting < 2) {
      throw new IllegalStateException(
          "expected at least two non-persisting segments to merge in store '"
              + name
              + "', but there are "
              + nonPersisting);
    }
    merging = List.copyOf(pipeline.subList(0, nonPersisting));
    final List<FlatSegment> oldestFirst = new ArrayList<>(merging);
    Collections.reverse(oldestFirst);
    return oldestFirst;
  }

  /**
   * Finishes an outstanding merge on the owner thread. On success, swaps {@code merged} in place of
   * the captured run: the run is located by identity — freezes may have prepended newer segments
   * and a persist-round completion may have dropped the persisted tail meanwhile, neither of which
   * reorders the run — the pipeline's references on the inputs are released (views still holding
   * them keep them and their chunks alive), and the annihilation ratio is re-measured for {@link
   * #mergeNeeded()}'s adaptive skip. On failure the run stays exactly as captured and the next
   * merge retries it; a discarded merge result is the caller's to release.
   */
  public void completeMerge(final FlatSegment merged, final boolean success) {
    if (merging == null) {
      throw new IllegalStateException(
          "expected an outstanding merge for store '" + name + "', but there is none");
    }
    final List<FlatSegment> captured = merging;
    merging = null;
    if (!success) {
      return;
    }
    Objects.requireNonNull(merged, "merged");
    int start = -1;
    for (int i = 0; i < pipeline.size(); i++) {
      if (pipeline.get(i) == captured.get(0)) {
        start = i;
        break;
      }
    }
    if (start < 0 || start + captured.size() > pipeline.size()) {
      throw new IllegalStateException(
          "expected the captured merge run of store '" + name + "' to still be in the pipeline");
    }
    final List<FlatSegment> run = pipeline.subList(start, start + captured.size());
    long entriesIn = 0;
    for (int i = 0; i < captured.size(); i++) {
      if (run.get(i) != captured.get(i)) {
        throw new IllegalStateException(
            "expected the captured merge run of store '" + name + "' to be contiguous");
      }
      entriesIn += captured.get(i).entryCount();
      pipelineBytes -= captured.get(i).byteSize();
    }
    run.clear();
    // release the pipeline's reference on the merged-away inputs only now — the merged segment
    // holds its own chunk references since its construction
    for (final FlatSegment input : captured) {
      input.release();
    }
    pipeline.add(start, merged);
    pipelineBytes += merged.byteSize();
    lastMergeAnnihilation = entriesIn == 0 ? 1.0 : 1.0 - merged.entryCount() / (double) entriesIn;
    metrics.countPipelineMerge();
    refreshPipelineStats();
  }

  /**
   * Marks every current pipeline segment as persisting and returns them oldest-first for draining.
   * The segments stay in the pipeline — readable and shadowing the delegate — until {@link
   * #completePersist(boolean)}. Throws {@link IllegalStateException} if a persist is already
   * outstanding (persist rounds are single-flight) or a merge is outstanding (a round captures
   * every pipeline segment, and segments captured by a round must never concurrently merge — see
   * {@link #beginMerge()}).
   */
  public List<FlatSegment> beginPersist() {
    if (persisting) {
      throw new IllegalStateException(
          "expected no outstanding persist round for store '"
              + name
              + "', but one is in flight (persist rounds are single-flight)");
    }
    if (merging != null) {
      throw new IllegalStateException(
          "expected no outstanding merge for store '"
              + name
              + "' when starting a persist round, but one is in flight");
    }
    persisting = true;
    persistingCount = pipeline.size();
    final List<FlatSegment> oldestFirst = new ArrayList<>(pipeline);
    Collections.reverse(oldestFirst);
    return List.copyOf(oldestFirst);
  }

  /**
   * On success, drops the persisting segments: the delegate now holds the newest persisted version
   * of every drained key, so reads fall through to it and repopulate the clean cache lazily via
   * normal read-through. Retirement is deliberately lazy — eagerly copying every drained live value
   * into the clean cache made completion cost proportional to the round's entry count in one
   * owner-thread burst; dropping is O(segments), and only the keys actually read again pay a
   * delegate probe. On failure, simply un-marks the segments — they stay in the pipeline and the
   * next round retries them.
   *
   * <p>Dropping without re-flagging newer overlay versions as flushed is sound: any staging or
   * active version written over a persisting live put already carries {@code flushed=true}, because
   * its write-time flushed check saw that live put in the pipeline (persisting segments stay in the
   * pipeline until this call), and the layers are disjoint, so no clean-cache hit could have
   * short-circuited that check.
   */
  public void completePersist(final boolean success) {
    if (!persisting) {
      throw new IllegalStateException(
          "expected an outstanding persist round for store '" + name + "', but there is none");
    }
    if (success) {
      final List<FlatSegment> persisted =
          pipeline.subList(pipeline.size() - persistingCount, pipeline.size());
      for (final FlatSegment segment : persisted) {
        pipelineBytes -= segment.byteSize();
        // the pipeline's reference; a view still holding the segment keeps its chunks alive, and
        // the last release returns the chunks to the pool for the next freezes
        segment.release();
      }
      persisted.clear();
      refreshPipelineStats();
    }
    persisting = false;
    persistingCount = 0;
  }

  /** Refreshes the volatile pipeline stat mirrors after a pipeline mutation (owner thread). */
  private void refreshPipelineStats() {
    pipelineSegments = pipeline.size();
    long entries = 0;
    for (final FlatSegment segment : pipeline) {
      entries += segment.entryCount();
    }
    pipelineEntries = entries;
    newestWatermark = pipeline.isEmpty() ? -1 : pipeline.get(0).watermark();
  }

  /**
   * The current pipeline segments, newest first — the immutable share of this store's state for a
   * {@link ReadOnlyView}. Includes segments currently persisting. The returned segments are
   * guaranteed alive only while the pipeline still holds them: a holder that outlives the next
   * pipeline mutation (a view, most notably) must {@link FlatSegment#retain()} them first.
   */
  public List<FlatSegment> segmentsNewestFirst() {
    return List.copyOf(pipeline);
  }

  // ------------------------------------------------------------------
  // Accounting
  // ------------------------------------------------------------------

  /** Approximate heap footprint of staging + active + pipeline + clean together. */
  public long approximateBytes() {
    return stagingBytes + activeBytes + pipelineBytes + cleanBytes;
  }

  /**
   * Approximate heap footprint of the pinned (not yet persisted) layers only: staging + active +
   * pipeline, excluding the evictable read cache — the writes a persist round would drain.
   */
  public long bufferedBytes() {
    return stagingBytes + activeBytes + pipelineBytes;
  }

  /** Whether any pinned layer (staging, active, pipeline) holds writes not yet persisted. */
  public boolean hasBufferedWrites() {
    return !stagingByKey.isEmpty() || !activeByKey.isEmpty() || !pipeline.isEmpty();
  }

  /**
   * Whether the active overlay holds committed writes a {@link #freeze(long)} would capture —
   * writes committed since the last freeze that read views cannot see yet.
   */
  public boolean hasActiveWrites() {
    return !activeByKey.isEmpty();
  }

  /** The watermark of the newest frozen segment, or -1 if the pipeline is empty. */
  public long newestSegmentWatermark() {
    return newestWatermark;
  }

  public long stagingBytes() {
    return stagingBytes;
  }

  public long activeBytes() {
    return activeBytes;
  }

  public long pipelineBytes() {
    return pipelineBytes;
  }

  public long cleanBytes() {
    return cleanBytes;
  }

  public long stagingEntryCount() {
    return stagingEntries;
  }

  public long activeEntryCount() {
    return activeEntries;
  }

  public long pipelineEntryCount() {
    return pipelineEntries;
  }

  public long cleanEntryCount() {
    return cleanEntries;
  }

  /** The number of frozen segments currently in the pipeline. */
  public int pipelineDepth() {
    return pipelineSegments;
  }

  /**
   * Whether pinned (un-evictable) entries hold the store over its byte budget — the signal to run a
   * persist round now.
   */
  public boolean overCapacity() {
    return approximateBytes() > maxBytes;
  }

  private void evictIfNeeded() {
    final Iterator<Map.Entry<ByteBuffer, byte[]>> eldestFirst = clean.entrySet().iterator();
    while (approximateBytes() > maxBytes && eldestFirst.hasNext()) {
      final Map.Entry<ByteBuffer, byte[]> eldest = eldestFirst.next();
      cleanBytes -= eldest.getKey().remaining() + eldest.getValue().length;
      eldestFirst.remove();
    }
    cleanEntries = clean.size();
  }

  private static long entrySize(final Entry entry) {
    return entry.key().length + (entry.tombstone() ? 0 : entry.value().length);
  }

  // ------------------------------------------------------------------
  // Scan-merge helpers
  // ------------------------------------------------------------------

  private static NavigableMap<byte[], Entry> prefixSelection(
      final TreeMap<byte[], Entry> sorted, final byte[] prefix) {
    if (prefix.length == 0) {
      return sorted;
    }
    final byte[] upper = prefixSuccessor(prefix);
    return upper == null ? sorted.tailMap(prefix, true) : sorted.subMap(prefix, true, upper, false);
  }

  /** The smallest key greater than every key prefixed by {@code prefix}; null for all-0xFF. */
  private static byte[] prefixSuccessor(final byte[] prefix) {
    for (int i = prefix.length - 1; i >= 0; i--) {
      if (prefix[i] != (byte) 0xFF) {
        final byte[] upper = Arrays.copyOf(prefix, i + 1);
        upper[i]++;
        return upper;
      }
    }
    return null;
  }
}
