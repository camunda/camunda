/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

import io.camunda.zeebe.db.layered.segment.FlatSegment;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Orchestrates the layered stores of one partition as a single durability unit: freezes them
 * together, drains them into one atomic {@link PersistBatch} together with the recovery anchor, and
 * publishes consistent {@link ReadOnlyView}s to asynchronous readers.
 *
 * <p>The persist protocol splits into three steps so only the cheap one runs on the owner thread:
 *
 * <ol>
 *   <li>{@link #prepareRound(long)} — owner thread. Marks every store's pipeline segments as
 *       persisting (they stay readable) and captures them into a {@link PersistRound}.
 *   <li>{@link PersistRound#persist()} — may run on an IO thread. Drains all captured segments,
 *       oldest first, newest version per key winning, into one {@link PersistBatch}; writes the
 *       recovery anchor (the newest drained watermark) into the <em>same</em> batch; commits.
 *       Touches only the immutable segments and the sink — never a store's mutable layers.
 *   <li>{@link #completeRound(PersistRound, boolean)} — owner thread. On success retires the
 *       drained segments in every store, rotates the snapshot (take new, release the old view's
 *       reference) and publishes a fresh view. On failure the segments stay in their pipelines and
 *       the next round retries them; nothing needs merging back.
 * </ol>
 *
 * <p>Rounds are single-flight: {@link #prepareRound(long)} throws while a round is outstanding. The
 * atomic batch is what upholds the anchor invariant — recovery either sees the full cut (state@P,
 * anchor=P) or none of it (state@P₀, anchor=P₀, replay rebuilds the difference); the torn states
 * (double application, holes) are unrepresentable.
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
 * locking.
 */
public final class LayeredStoreCoordinator implements AutoCloseable {

  private final Map<String, LayeredKeyValueStore> stores;
  private final PersistSink sink;
  private final SnapshotSource snapshots;
  private final Consumer<ReadOnlyView> viewListener;

  private ReadOnlyView currentView;
  private PersistRound outstandingRound;

  /**
   * @param stores the stores forming the durability unit; names must be unique
   * @param sink creates the atomic persist batches and reads the anchor at recovery
   * @param snapshots pins durable-state snapshots for views
   * @param viewListener receives every newly published view; the coordinator releases its reference
   *     on the previous view once the new one is published, so the previous snapshot closes when
   *     its last reader releases (see the class javadoc's view lifecycle)
   */
  public LayeredStoreCoordinator(
      final Collection<LayeredKeyValueStore> stores,
      final PersistSink sink,
      final SnapshotSource snapshots,
      final Consumer<ReadOnlyView> viewListener) {
    this.sink = Objects.requireNonNull(sink, "sink");
    this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    this.viewListener = Objects.requireNonNull(viewListener, "viewListener");
    final Map<String, LayeredKeyValueStore> byName = new LinkedHashMap<>();
    for (final LayeredKeyValueStore store : stores) {
      if (byName.put(store.name(), store) != null) {
        throw new IllegalArgumentException(
            "Duplicate store name '%s' in the durability unit".formatted(store.name()));
      }
    }
    this.stores = byName;
    // publish an initial view right away so asynchronous readers always have one to hold
    publishView(new RefCountedSnapshot(snapshots.takeSnapshot()));
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
   * Starts a persist round over everything currently frozen, implicitly freezing first if the
   * active overlays hold anything at {@code watermark}. Owner thread.
   *
   * @throws IllegalStateException if a round is already outstanding
   */
  public PersistRound prepareRound(final long watermark) {
    if (outstandingRound != null) {
      throw new IllegalStateException(
          "expected no outstanding persist round, but one is in flight"
              + " (persist rounds are single-flight)");
    }
    freezeAll(watermark);
    final Map<String, List<FlatSegment>> capturedOldestFirst = new LinkedHashMap<>();
    long anchor = -1;
    for (final LayeredKeyValueStore store : stores.values()) {
      final List<FlatSegment> oldestFirst = store.beginPersist();
      capturedOldestFirst.put(store.name(), oldestFirst);
      for (final FlatSegment segment : oldestFirst) {
        anchor = Math.max(anchor, segment.watermark());
      }
    }
    outstandingRound = new PersistRound(sink, capturedOldestFirst, anchor);
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
      // rotate: publish the fresh cut first, then release the coordinator's reference on the old
      // view — its snapshot closes once the last reader still holding it releases too
      final ReadOnlyView previous = currentView;
      publishView(new RefCountedSnapshot(snapshots.takeSnapshot()));
      previous.release();
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
   * thread by {@link #persist()}. Never touches a store's mutable layers.
   */
  public static final class PersistRound {

    private static final byte[] EMPTY_PREFIX = new byte[0];

    private final PersistSink sink;
    private final Map<String, List<FlatSegment>> capturedOldestFirst;
    private final long anchor;

    private PersistRound(
        final PersistSink sink,
        final Map<String, List<FlatSegment>> capturedOldestFirst,
        final long anchor) {
      this.sink = sink;
      this.capturedOldestFirst = capturedOldestFirst;
      this.anchor = anchor;
    }

    /**
     * Drains the captured segments into one atomic batch — entries plus anchor — and commits. The
     * only coordinator operation allowed off the owner thread. Throws on failure; the caller
     * reports the outcome via {@link #completeRound(PersistRound, boolean)} either way.
     */
    public void persist() throws Exception {
      if (capturedOldestFirst.values().stream().allMatch(List::isEmpty)) {
        return; // nothing captured — no state to persist, no anchor to advance
      }
      try (final PersistBatch batch = sink.newBatch()) {
        for (final Map.Entry<String, List<FlatSegment>> captured : capturedOldestFirst.entrySet()) {
          drainStore(batch, captured.getKey(), captured.getValue());
        }
        if (anchor >= 0) {
          batch.putAnchor(anchor);
        }
        batch.commit();
      }
    }

    private static void drainStore(
        final PersistBatch batch, final String storeName, final List<FlatSegment> oldestFirst) {
      if (oldestFirst.isEmpty()) {
        return;
      }
      // Pre-merging materializes one segment per store, but it is what computes the sticky
      // flushed-OR across shadowed versions — the skip decision below needs "was ANY version of
      // this key ever flushed", which the final version's flag alone cannot answer. In-memory
      // pipeline compaction usually collapsed most of it already, so the materialization is small.
      final FlatSegment merged = FlatSegment.merge(oldestFirst, false);
      final Iterator<Entry> entries = merged.range(EMPTY_PREFIX);
      while (entries.hasNext()) {
        final Entry entry = entries.next();
        if (!entry.tombstone()) {
          batch.put(storeName, entry.key(), entry.value());
        } else if (entry.flushed()) {
          batch.delete(storeName, entry.key());
        }
        // a never-flushed tombstone is skipped entirely: the pair annihilated in memory and the
        // durable store never held the key
      }
    }

    /** The anchor position this round will commit (newest captured watermark), or -1. */
    public long anchor() {
      return anchor;
    }
  }
}
