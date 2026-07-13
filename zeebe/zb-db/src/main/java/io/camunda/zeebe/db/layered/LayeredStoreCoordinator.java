/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

import io.camunda.zeebe.db.layered.segment.FlatSegment;
import io.camunda.zeebe.db.layered.segment.FlushedOrMergeIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Orchestrates the layered stores of one partition as a single durability unit: freezes them
 * together, drains them into one atomic {@link PersistBatch} together with the recovery anchor, and
 * publishes consistent {@link ReadOnlyView}s to asynchronous readers.
 *
 * <p>The persist protocol splits into three steps so only the cheap one runs on the owner thread:
 *
 * <ol>
 *   <li>{@link #prepareRound(long)} — owner thread, O(1) per store. Swaps every store's active
 *       overlay out as its raw captured tip (no flatten — see {@link
 *       LayeredKeyValueStore#beginPersist(long)}), marks the pipeline segments as persisting (tip
 *       and segments stay readable) and captures both into a {@link PersistRound}.
 *   <li>{@link PersistRound#persist()} — may run on an IO thread. Drains all captured state — the
 *       raw tips walked directly as sorted maps, the segments via their ranges — oldest first,
 *       newest version per key winning, into one {@link PersistBatch}; writes the recovery anchor
 *       (the newest drained watermark) into the <em>same</em> batch; commits. Touches only the
 *       immutable captured structures and the sink — never a store's mutable layers. Alternatively
 *       the drain runs paced, in {@link PersistRound#persistSlice(long) sub-batch slices} that each
 *       commit their own batch — see the anchor-last invariant on {@link PersistRound}.
 *   <li>{@link #completeRound(PersistRound, boolean)} — owner thread. On success drops the drained
 *       segments and tips in every store (the durable state is authoritative for them now; clean
 *       caches refill lazily via read-through), rotates the snapshot (take new, release the old
 *       view's reference) and publishes a fresh view. On failure the segments stay in their
 *       pipelines — a raw tip becomes a normal frozen segment — and the next round retries them;
 *       nothing needs merging back.
 * </ol>
 *
 * <p>Rounds are single-flight: {@link #prepareRound(long)} throws while a round is outstanding. The
 * atomic batch is what upholds the anchor invariant — recovery either sees the full cut (state@P,
 * anchor=P) or none of it (state@P₀, anchor=P₀, replay rebuilds the difference); the torn states
 * (double application, holes) are unrepresentable.
 *
 * <p><b>Pipeline merges</b> follow the same three-step shape so their k-way walk also leaves the
 * owner thread: {@link #prepareMerge()} captures the over-limit non-persisting run of every store
 * that needs one (owner thread), {@link MergeRound#merge()} builds the merged segments — index-only
 * over immutable inputs, so safe on an IO thread — and {@link #completeMerge(MergeRound, boolean)}
 * swaps them in (owner thread). Merges are single-flight and mutually exclusive with rounds in one
 * direction: a merge may start while a round is outstanding (it captures only non-persisting runs,
 * disjoint from the round's segments), but a round must not start while a merge is outstanding — a
 * round captures every pipeline segment, and segments captured by a round must never concurrently
 * merge; {@link #prepareRound(long)} throws until the merge completed.
 *
 * <p>{@link #freezeAll(long)} freezes every store at a common watermark and publishes a refreshed
 * view — segments change, the snapshot stays, which is consistent because the durable state has not
 * moved (persist rounds are its only writer).
 *
 * <p><b>View lifecycle:</b> views and their pinned snapshots are reference-counted so multiple
 * concurrent readers can each hold one safely. Every published view owns one reference on its
 * snapshot, and the coordinator holds exactly that reference for the current view. On rotation
 * (successful {@link #completeRound(PersistRound, boolean)}) the coordinator releases its reference
 * on the previous view <em>after</em> publishing the new one — the old snapshot closes only when
 * the last reader also releases, never under a reader mid-scan. A {@link #freezeAll(long)}
 * republish reuses the same snapshot: the new view retains it, the previous view's reference is
 * released, and the net count is unchanged, so the shared snapshot survives across view
 * generations. {@link #close()} releases the coordinator's reference on the current view.
 *
 * <p><b>Threading:</b> owner thread only, except {@link PersistRound#persist()} and the retain /
 * release calls on published views, which readers may issue from any thread. Hand-offs (round to IO
 * thread, views to readers) must happen through a safe publication mechanism — wire the view
 * listener to a {@link ViewPublisher} and let readers pair {@link ViewPublisher#acquireLatest()}
 * with {@link ViewPublisher#release(ReadOnlyView)}. The coordinator itself contains no internal
 * locking. The gauge callbacks registered on the metrics (anchor lag, round in flight) are the one
 * further exception: a metrics scrape thread may poll them at any time, so they read only {@code
 * volatile} fields and the stores' volatile stat mirrors — never the owner-mutable structures.
 */
public final class LayeredStoreCoordinator implements AutoCloseable {

  private final Map<String, LayeredKeyValueStore> stores;
  private final PersistSink sink;
  private final SnapshotSource snapshots;
  private final Consumer<ReadOnlyView> viewListener;
  private final LayeredStoreMetrics metrics;
  // the designated anchor-carrying entries, deferred to a paced drain's final slice; owner thread
  private final Map<String, byte[]> anchorEntries = new HashMap<>();

  private ReadOnlyView currentView;
  // volatile because the gauge callbacks read them from the metrics scrape thread (single writer:
  // the owner thread) — see the Threading javadoc
  private volatile PersistRound outstandingRound;
  private volatile long lastPersistedAnchor = -1;
  // owner thread only — no gauge reads it
  private MergeRound outstandingMerge;

  /**
   * @param stores the stores forming the durability unit; names must be unique
   * @param sink creates the atomic persist batches and reads the anchor at recovery
   * @param snapshots pins durable-state snapshots for views
   * @param viewListener receives every newly published view; the coordinator releases its reference
   *     on the previous view once the new one is published, so the previous snapshot closes when
   *     its last reader releases (see the class javadoc's view lifecycle)
   * @param metrics receives the persist-round and view instrumentation of this durability unit (use
   *     {@link LayeredStoreMetrics#noop()} for uninstrumented wirings)
   */
  public LayeredStoreCoordinator(
      final Collection<LayeredKeyValueStore> stores,
      final PersistSink sink,
      final SnapshotSource snapshots,
      final Consumer<ReadOnlyView> viewListener,
      final LayeredStoreMetrics metrics) {
    this.sink = Objects.requireNonNull(sink, "sink");
    this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    this.viewListener = Objects.requireNonNull(viewListener, "viewListener");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    final Map<String, LayeredKeyValueStore> byName = new LinkedHashMap<>();
    for (final LayeredKeyValueStore store : stores) {
      if (byName.put(store.name(), store) != null) {
        throw new IllegalArgumentException(
            "Duplicate store name '%s' in the durability unit".formatted(store.name()));
      }
    }
    this.stores = byName;
    metrics.registerAnchorLag(this::anchorLag);
    metrics.registerRoundInFlight(() -> outstandingRound != null ? 1 : 0);
    // publish an initial view right away so asynchronous readers always have one to hold
    publishView(new RefCountedSnapshot(snapshots.takeSnapshot()));
  }

  /**
   * How many log positions the durable state trails the buffered state: newest frozen segment
   * watermark minus the anchor of the last successful round, zero when nothing is frozen. Runs on
   * the metrics scrape thread — reads only volatiles ({@code stores} is immutable after
   * construction, {@link LayeredKeyValueStore#newestSegmentWatermark()} is a volatile mirror).
   */
  private long anchorLag() {
    long newestFrozen = -1;
    for (final LayeredKeyValueStore store : stores.values()) {
      newestFrozen = Math.max(newestFrozen, store.newestSegmentWatermark());
    }
    if (newestFrozen < 0) {
      return 0;
    }
    return Math.max(0, newestFrozen - Math.max(0, lastPersistedAnchor));
  }

  /**
   * Freezes every store's active overlay into a segment stamped {@code watermark} and publishes a
   * refreshed view. Cheap — pointer swaps and flattens, no durable IO.
   */
  public void freezeAll(final long watermark) {
    for (final LayeredKeyValueStore store : stores.values()) {
      store.freeze(watermark);
    }
    // the durable state has not moved (persist rounds are its only writer), so the current
    // snapshot still matches the new segment set: the new view retains the same snapshot, then
    // the previous view's reference is released — net count unchanged, the snapshot survives
    final ReadOnlyView previous = currentView;
    final RefCountedSnapshot shared = previous.snapshotRef();
    shared.retain();
    publishView(shared);
    previous.release();
  }

  /**
   * Designates the entry {@code storeName}/{@code key} as the recovery anchor's carrier in wirings
   * where the anchor is a normal drained key rather than a separate sink cell (see {@link
   * PersistBatch#putAnchor(long)}). A paced drain defers designated entries to the round's final
   * slice, upholding the anchor-last invariant: no partial slice can ever commit an anchor ahead of
   * the state it describes. Owner thread; re-designating (successor owners of a reused database do)
   * overwrites.
   *
   * @throws IllegalArgumentException if no store of this durability unit has that name
   */
  public void designateAnchorEntry(final String storeName, final byte[] key) {
    if (!stores.containsKey(storeName)) {
      throw new IllegalArgumentException(
          "Unknown store '%s' for the anchor entry; known stores: %s"
              .formatted(storeName, stores.keySet()));
    }
    anchorEntries.put(storeName, Objects.requireNonNull(key, "key").clone());
  }

  /**
   * Starts a persist round over everything buffered at {@code watermark}. O(1) per store on the
   * owner thread: active overlays are swapped out whole as raw captured tips — never flattened
   * here; the drain walks the swapped sorted maps directly on the IO thread — and the pipeline
   * segments are marked persisting. Deliberately publishes no view: capturing moves nothing that a
   * view could already see (views resolve segments, and the tips were invisible active state), and
   * the freshness consumers self-freshen through {@link #freezeAll(long)} barriers, which
   * materialize raw tips into view-visible segments first.
   *
   * @throws IllegalStateException if a round or a merge is already outstanding, or a store's
   *     staging layer is not empty (a batch is in flight)
   */
  public PersistRound prepareRound(final long watermark) {
    if (outstandingRound != null) {
      throw new IllegalStateException(
          "expected no outstanding persist round, but one is in flight"
              + " (persist rounds are single-flight)");
    }
    if (outstandingMerge != null) {
      throw new IllegalStateException(
          "expected no outstanding merge when preparing a persist round, but one is in flight"
              + " (segments captured by a round must never concurrently merge)");
    }
    final Map<String, PersistRound.CapturedStore> captured = new LinkedHashMap<>();
    long anchor = -1;
    long capturedBytes = 0;
    for (final LayeredKeyValueStore store : stores.values()) {
      final LayeredKeyValueStore.PersistCapture capture = store.beginPersist(watermark);
      for (final FlatSegment segment : capture.segmentsOldestFirst()) {
        anchor = Math.max(anchor, segment.watermark());
        capturedBytes += segment.byteSize();
      }
      if (capture.tip() != null) {
        anchor = Math.max(anchor, watermark);
        capturedBytes += capture.tipBytes();
      }
      captured.put(
          store.name(),
          new PersistRound.CapturedStore(capture.tip(), capture.segmentsOldestFirst()));
    }
    outstandingRound =
        new PersistRound(sink, captured, anchor, Map.copyOf(anchorEntries), capturedBytes, metrics);
    return outstandingRound;
  }

  /**
   * Finishes an outstanding round on the owner thread after {@link PersistRound#persist()} returned
   * (successfully or not). See the class javadoc for success/failure semantics.
   */
  public void completeRound(final PersistRound round, final boolean success) {
    if (outstandingRound == null) {
      throw new IllegalStateException(
          "expected an outstanding persist round to complete, but there is none");
    }
    if (round != outstandingRound) {
      throw new IllegalStateException(
          "expected the outstanding persist round, but got a different one");
    }
    outstandingRound = null;
    for (final LayeredKeyValueStore store : stores.values()) {
      store.completePersist(success);
    }
    if (success) {
      if (round.anchor() >= 0) {
        lastPersistedAnchor = round.anchor();
      }
      // rotate: publish the fresh cut first, then release the coordinator's reference on the old
      // view — its snapshot closes once the last reader still holding it releases too
      final ReadOnlyView previous = currentView;
      publishView(new RefCountedSnapshot(snapshots.takeSnapshot()));
      previous.release();
      metrics.countViewRotation();
    } else {
      metrics.countRoundFailure();
    }
    // on failure the segments stayed in their pipelines and the durable state did not move, so
    // the current view is still valid — nothing to republish
  }

  /**
   * The most recently published view — one is published at construction, so this is never null
   * until {@link #close()}.
   */
  public ReadOnlyView currentView() {
    return currentView;
  }

  /** Whether a persist round is outstanding (prepared but not completed). */
  public boolean roundOutstanding() {
    return outstandingRound != null;
  }

  /**
   * Captures a merge round over every store whose pipeline needs merging (see {@link
   * LayeredKeyValueStore#mergeNeeded()}), or returns null when no store does. Owner thread; the
   * returned round's {@link MergeRound#merge()} may then run on an IO thread. May be called while a
   * persist round is outstanding — the captured runs exclude the round's persisting segments.
   *
   * @throws IllegalStateException if a merge is already outstanding (merges are single-flight)
   */
  public @Nullable MergeRound prepareMerge() {
    if (outstandingMerge != null) {
      throw new IllegalStateException(
          "expected no outstanding merge, but one is in flight (merges are single-flight)");
    }
    final List<MergeRound.CapturedRun> runs = new ArrayList<>();
    for (final LayeredKeyValueStore store : stores.values()) {
      if (store.mergeNeeded()) {
        runs.add(
            new MergeRound.CapturedRun(store.name(), store.beginMerge(), store.absorbsDeletes()));
      }
    }
    if (runs.isEmpty()) {
      return null;
    }
    outstandingMerge = new MergeRound(runs);
    return outstandingMerge;
  }

  /**
   * Finishes an outstanding merge on the owner thread after {@link MergeRound#merge()} returned
   * (successfully or not). On success every captured run is swapped for its merged segment; on
   * failure the runs stay in their pipelines, any merged segments the round did build are released
   * (recycling their chunk references), and the next merge retries.
   */
  public void completeMerge(final MergeRound round, final boolean success) {
    if (outstandingMerge == null) {
      throw new IllegalStateException(
          "expected an outstanding merge to complete, but there is none");
    }
    if (round != outstandingMerge) {
      throw new IllegalStateException("expected the outstanding merge, but got a different one");
    }
    outstandingMerge = null;
    for (final MergeRound.CapturedRun run : round.runs()) {
      stores.get(run.store()).completeMerge(success ? round.merged(run.store()) : null, success);
    }
    if (!success) {
      round.releaseMergedSegments();
    }
  }

  /** Whether a merge is outstanding (prepared but not completed). */
  public boolean mergeOutstanding() {
    return outstandingMerge != null;
  }

  /**
   * Completes an outstanding merge as failed without waiting for its {@link MergeRound#merge()} —
   * recovery for a successor owner taking over stores whose previous owner died between preparing
   * and completing a merge. The captured runs stay in their pipelines, unmerged; a merged result
   * the orphaned round did build is abandoned to the garbage collector together with its chunk
   * references (the chunks are simply never reused — never recycled under a holder). A no-op when
   * no merge is outstanding.
   *
   * <p><b>Precondition:</b> the previous owner's merge IO must no longer be running, mirroring
   * {@link #abortOutstandingRound()}.
   */
  public void abortOutstandingMerge() {
    if (outstandingMerge != null) {
      // deliberately do not release the orphaned round's merged segments: there is no safe
      // publication from the dead owner's merge IO thread to this one, so the round's result map
      // must not be read — leaking the results to the garbage collector is safe (chunks are never
      // recycled under a holder), reading a torn map would not be
      final MergeRound orphaned = outstandingMerge;
      outstandingMerge = null;
      for (final MergeRound.CapturedRun run : orphaned.runs()) {
        stores.get(run.store()).completeMerge(null, false);
      }
    }
  }

  /**
   * Completes an outstanding round as failed without waiting for its {@link PersistRound#persist()}
   * — recovery for a successor owner taking over stores whose previous owner died between preparing
   * and completing a round. The segments stay in their pipelines and the successor's next round
   * retries them; if the orphaned round's atomic batch did commit before the owner died, the retry
   * merely rewrites the same versions, which is idempotent. A no-op when no round is outstanding.
   *
   * <p>Only sound for wirings whose drains are all-or-nothing (a single {@link
   * PersistRound#persist()} batch). An orphaned <em>paced</em> drain may have committed partial
   * slices without the anchor; aborting leaves that torn durable cut in place — masked from layered
   * readers by the retained segments, but visible to direct readers of the durable store until a
   * later round succeeds. Successors of paced drains must use {@link
   * #completeOutstandingRoundForward()} instead.
   *
   * <p><b>Precondition:</b> the previous owner's persist IO must no longer be running (its IO
   * executor terminated) — otherwise the orphaned {@code persist()} could race a new round on the
   * shared sink.
   */
  public void abortOutstandingRound() {
    if (outstandingRound != null) {
      completeRound(outstandingRound, false);
    }
  }

  /**
   * Completes an outstanding round a previous owner left behind by draining it — recovery for a
   * successor taking over stores whose previous owner died mid-round, replacing {@link
   * #abortOutstandingRound()} where drains are paced: the orphaned round may have committed partial
   * slices without the anchor, and completing it forward restores the durable anchor invariant
   * (state@P with anchor=P) instead of leaving the torn cut in place. The re-drain starts from
   * scratch over the round's immutable captured segments — rewriting versions a partial slice
   * already committed is idempotent (newest-wins) — and commits the anchor in its final slice. Runs
   * inline on the calling owner thread; a no-op when no round is outstanding.
   *
   * <p>On drain failure the round completes as failed and the failure is rethrown: the captured
   * segments stay in their pipelines and keep shadowing the durable store — the partial slices stay
   * masked from layered readers — and the successor's next round retries them.
   *
   * <p><b>Precondition:</b> as for {@link #abortOutstandingRound()}: the previous owner's persist
   * IO must no longer be running. The orphaned drain cursor the dead owner's IO thread may have
   * left behind is never read — {@link PersistRound#persist()} always drains from scratch.
   */
  public void completeOutstandingRoundForward() throws Exception {
    if (outstandingRound == null) {
      return;
    }
    final PersistRound orphaned = outstandingRound;
    try {
      orphaned.persist();
    } catch (final Exception e) {
      completeRound(orphaned, false);
      throw e;
    }
    completeRound(orphaned, true);
  }

  /** The recovery anchor as last committed by a round, or -1; delegates to the sink. */
  public long persistedAnchor() {
    return sink.readAnchor();
  }

  /**
   * Releases the coordinator's reference on the current view; the snapshot closes once the last
   * reader releases too. Owner thread, idempotent; the coordinator must not be used afterwards (and
   * {@link #currentView()} returns null).
   */
  @Override
  public void close() {
    if (currentView != null) {
      currentView.release();
      currentView = null;
    }
  }

  private void publishView(final RefCountedSnapshot snapshotRef) {
    final Map<String, List<FlatSegment>> segments = new HashMap<>();
    stores.forEach((name, store) -> segments.put(name, store.segmentsNewestFirst()));
    currentView = new ReadOnlyView(segments, snapshotRef);
    viewListener.accept(currentView);
  }

  /**
   * One prepared persist round: the immutable segments captured from every store, drained on an IO
   * thread by {@link #persist()} — or paced, in sub-batch slices, by repeated {@link
   * #persistSlice(long)} calls. Never touches a store's mutable layers.
   *
   * <p><b>Anchor-last invariant of a paced drain:</b> every slice commits its own batch, so a crash
   * (or owner death) mid-round leaves partial slices committed. That is safe in exactly one
   * direction — state may run ahead of the anchor (a re-drain rewrites the same versions,
   * newest-wins, idempotently), the anchor must never run ahead of the state it describes.
   * Therefore the anchor, and nothing else, rides only in the final slice: {@link
   * PersistBatch#putAnchor(long)} is issued there, and entries {@link #designateAnchorEntry(String,
   * byte[]) designated} as anchor carriers (wirings where the anchor is a normal drained key) are
   * held back from the data slices and written in the final slice too.
   *
   * <p><b>Threading:</b> the drain cursor is confined to the single thread driving the drain (the
   * IO thread, or the owner thread for inline drains and stale-round completion); {@link
   * #persist()} always discards any previous cursor and drains from scratch, which is what makes it
   * safe as the successor's re-drain of an orphaned round. {@link #progress()} may be read from
   * another thread for pacing — it reads a volatile counter only.
   */
  public static final class PersistRound {

    private static final byte[] EMPTY_PREFIX = new byte[0];

    private final PersistSink sink;
    private final Map<String, CapturedStore> captured;
    private final long anchor;
    private final Map<String, byte[]> anchorEntries;
    private final long capturedBytes;
    private final LayeredStoreMetrics metrics;

    private Drain drain;
    // written by the drain thread, read by progress() callers — see the Threading javadoc
    private volatile long drainedBytesVolatile;

    private PersistRound(
        final PersistSink sink,
        final Map<String, PersistRound.CapturedStore> captured,
        final long anchor,
        final Map<String, byte[]> anchorEntries,
        final long capturedBytes,
        final LayeredStoreMetrics metrics) {
      this.sink = sink;
      this.captured = captured;
      this.anchor = anchor;
      this.anchorEntries = anchorEntries;
      this.capturedBytes = capturedBytes;
      this.metrics = metrics;
    }

    /**
     * Drains the captured state into one atomic batch — entries plus anchor — and commits. Always
     * drains from scratch, discarding any cursor a previous (possibly dead) driver left behind.
     * Throws on failure; the caller reports the outcome via {@link #completeRound(PersistRound,
     * boolean)} either way.
     */
    public void persist() throws Exception {
      drain = null;
      persistSlice(Long.MAX_VALUE);
    }

    /**
     * Drains the next slice of the captured state — at least {@code minSliceBytes} of consumed
     * entry bytes, or everything that remains — into a batch of its own and commits it. Returns
     * true when the round is fully drained. The {@link PersistBatch#putAnchor(long) anchor} and the
     * designated anchor-carrying entries ride only in the final slice — never in a data slice (see
     * the class javadoc for the anchor-last invariant); the final slice additionally carries
     * whatever tail of data its batch consumed before the stream exhausted. A slice throw ends the
     * drain; the caller completes the round as failed and a retry re-drains from scratch.
     *
     * @return true once the final slice committed; false while data slices remain
     * @throws IllegalStateException if the round was already fully drained (complete it instead)
     */
    public boolean persistSlice(final long minSliceBytes) throws Exception {
      if (drain == null) {
        if (captured.values().stream().allMatch(CapturedStore::isEmpty)) {
          return true; // nothing captured — no state to persist, no anchor to advance
        }
        drain = new Drain();
      }
      if (drain.finished) {
        throw new IllegalStateException(
            "expected an unfinished drain, but the round is already fully drained");
      }
      boolean committed = false;
      try (final PersistBatch batch = sink.newBatch()) {
        long sliceBytes = 0;
        while (sliceBytes < minSliceBytes) {
          final Entry entry = drain.nextEntry();
          if (entry == null) {
            // the stream is exhausted: this is the final slice — anchor carriers and anchor only
            // (plus whatever data this slice consumed before exhausting, on the unpaced path)
            for (final HeldAnchorWrite held : drain.heldAnchorWrites) {
              write(batch, held.store(), held.entry());
            }
            if (anchor >= 0) {
              batch.putAnchor(anchor);
            }
            batch.commit();
            committed = true;
            drain.finished = true;
            metrics.countPersistSlice();
            metrics.observeRoundDuration(System.nanoTime() - drain.startedNanos);
            return true;
          }
          drain.consumed(entry);
          final byte[] designated = anchorEntries.get(drain.currentStore);
          if (designated != null && Arrays.equals(designated, entry.key())) {
            // anchor carrier: held back so no data slice can commit an anchor ahead of the state
            drain.heldAnchorWrites.add(new HeldAnchorWrite(drain.currentStore, entry));
            continue;
          }
          sliceBytes += write(batch, drain.currentStore, entry);
        }
        batch.commit();
        committed = true;
        metrics.countPersistSlice();
        return false;
      } finally {
        if (!committed) {
          // a failed slice ends this drain attempt; measure the round up to the failure
          metrics.observeRoundDuration(System.nanoTime() - drain.startedNanos);
        }
      }
    }

    /** Writes one merged entry into the batch and returns the bytes it added. */
    private long write(final PersistBatch batch, final String storeName, final Entry entry) {
      if (!entry.tombstone()) {
        batch.put(storeName, entry.key(), entry.value());
        metrics.countDrainedEntry(entry.key().length, entry.value().length);
        return entry.key().length + entry.value().length;
      }
      if (entry.flushed()) {
        batch.delete(storeName, entry.key());
        metrics.countDrainedEntry(entry.key().length, 0);
        return entry.key().length;
      }
      // a never-flushed tombstone is skipped entirely: the pair annihilated in memory and the
      // durable store never held the key
      metrics.countDrainSkippedTombstone();
      return 0;
    }

    /**
     * The fraction of the captured entry bytes the drain has consumed so far, in [0, 1] — the
     * progress a pacing driver compares against its deadline. Deduplicated (shadowed) versions
     * count as consumed when their newest version is, so progress can only lag the true completion
     * fraction — the safe direction for pacing (never slower than the deadline asks). Readable from
     * any thread.
     */
    public double progress() {
      if (capturedBytes <= 0) {
        return 1.0;
      }
      return Math.min(1.0, drainedBytesVolatile / (double) capturedBytes);
    }

    /** The anchor position this round will commit (newest captured watermark), or -1. */
    public long anchor() {
      return anchor;
    }

    /** An anchor-carrying entry held back for the final slice. */
    private record HeldAnchorWrite(String store, Entry entry) {}

    /**
     * One store's captured share of a round: the raw tip (the swapped-out active overlay, the
     * newest layer of the drain merge — never flattened; the drain walks the sorted map directly)
     * and the pipeline segments, oldest first.
     */
    private record CapturedStore(
        @Nullable NavigableMap<byte[], Entry> tip, List<FlatSegment> segmentsOldestFirst) {

      private boolean isEmpty() {
        return tip == null && segmentsOldestFirst.isEmpty();
      }
    }

    /**
     * The cursor of one drain attempt: a per-store streamed merge (see {@link
     * FlushedOrMergeIterator} for why the merge is streamed, not materialized — memory stays
     * bounded by the k stream cursors), walked store by store across slice boundaries.
     */
    private final class Drain {

      private final Iterator<Map.Entry<String, CapturedStore>> stores =
          captured.entrySet().iterator();
      private final List<HeldAnchorWrite> heldAnchorWrites = new ArrayList<>(1);
      private final long startedNanos = System.nanoTime();
      private String currentStore;
      private Iterator<Entry> currentEntries;
      private boolean finished;

      /** The next merged entry across all stores, or null when every stream is exhausted. */
      private Entry nextEntry() {
        while (currentEntries == null || !currentEntries.hasNext()) {
          if (!stores.hasNext()) {
            return null;
          }
          final Map.Entry<String, CapturedStore> next = stores.next();
          currentStore = next.getKey();
          currentEntries = mergedStream(next.getValue());
        }
        return currentEntries.next();
      }

      private void consumed(final Entry entry) {
        drainedBytesVolatile += entry.key().length + (entry.tombstone() ? 0 : entry.value().length);
      }

      private Iterator<Entry> mergedStream(final CapturedStore store) {
        final List<FlatSegment> oldestFirst = store.segmentsOldestFirst();
        final List<Iterator<Entry>> newestFirst = new ArrayList<>(1 + oldestFirst.size());
        if (store.tip() != null) {
          // the raw tip is the newest layer of the merge (it holds every post-freeze version)
          newestFirst.add(store.tip().values().iterator());
        }
        for (int i = oldestFirst.size() - 1; i >= 0; i--) {
          newestFirst.add(oldestFirst.get(i).range(EMPTY_PREFIX));
        }
        return new FlushedOrMergeIterator(newestFirst);
      }
    }
  }

  /**
   * One prepared merge round: the immutable non-persisting runs captured from every store that
   * needed merging, collapsed by {@link #merge()} — which may run on an IO thread — and swapped in
   * on the owner thread by {@link #completeMerge(MergeRound, boolean)}. Never touches a store's
   * mutable layers; the results cross back to the owner thread through the driver's future
   * hand-off, which is the required safe publication.
   */
  public static final class MergeRound {

    private final List<CapturedRun> runs;
    private final Map<String, FlatSegment> mergedByStore = new HashMap<>();

    private MergeRound(final List<CapturedRun> runs) {
      this.runs = List.copyOf(runs);
    }

    /**
     * Builds the merged segment of every captured run — index-only k-way walks over immutable
     * inputs (refs move, bytes don't; see {@link FlatSegment#merge(List, boolean)}), the only merge
     * operation allowed off the owner thread. Throws on failure; the caller reports the outcome via
     * {@link #completeMerge(MergeRound, boolean)} either way.
     */
    public void merge() {
      for (final CapturedRun run : runs) {
        mergedByStore.put(run.store(), FlatSegment.merge(run.oldestFirst(), run.absorbDeletes()));
      }
    }

    private List<CapturedRun> runs() {
      return runs;
    }

    private FlatSegment merged(final String store) {
      return mergedByStore.get(store);
    }

    /** Releases the merged segments a failed round built, recycling their chunk references. */
    private void releaseMergedSegments() {
      mergedByStore.values().forEach(FlatSegment::release);
      mergedByStore.clear();
    }

    private record CapturedRun(
        String store, List<FlatSegment> oldestFirst, boolean absorbDeletes) {}
  }
}
