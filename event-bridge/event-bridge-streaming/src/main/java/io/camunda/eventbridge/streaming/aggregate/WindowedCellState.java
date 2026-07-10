/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import io.camunda.eventbridge.streaming.window.Windowed;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/**
 * The heap working set of a segment aggregation: the open {@code (key, window) -> accumulator}
 * cells, plus the bookkeeping around them. It owns pure structure — which cells are open, in which
 * window-end order they become due, and which have changed or been evicted since the last flush /
 * checkpoint — and keeps those views consistent with each other. <em>When</em> a cell is folded,
 * finalized, or persisted is the owning aggregation's policy (segment boundaries for {@link
 * SegmentSealingAggregation}, watermark/grace/drained finalization for {@link
 * SegmentMergingAggregation}).
 *
 * <p>{@link #get(Windowed)} is safe for view-key probes (e.g. a byte-backed key wrapping a shared
 * buffer): the probe is only compared, never stored. On the insert path the caller must {@link
 * #put(Windowed, Object)} a cell whose key it owns; on the update path the backing map keeps its
 * existing (owned) key, so putting the probe-keyed cell back is safe.
 *
 * <p>The window-end index and the changed/evicted sets are maintained only for the cells the owner
 * feeds into {@link #index(Windowed, long)} and {@link #markChanged(Windowed)} — an owner that
 * needs no due-window finalization (the sealing side) never indexes, and one with no durable
 * checkpoint (Model R sealing) never marks.
 *
 * <p>The checkpoint delta supports being <em>detached</em>: {@link #detachCheckpointDelta()} hands
 * the changed/evicted sets over for an asynchronous persist and installs fresh empty ones, so
 * folding continues while the detached cut is written; a failed persist {@link
 * #mergeBackCheckpointDelta(CheckpointDelta) merges it back} so the next cut re-includes it.
 *
 * @param <K> the base grouping key type
 * @param <ACC> the accumulator type
 */
final class WindowedCellState<K, ACC> {

  private final Map<Windowed<K>, ACC> open = new HashMap<>();
  // Secondary index of the open cells by window end. A cell's end (windowStart + size) is constant,
  // so maintenance is insert-on-first-touch + remove-on-finalize — and evictDue pops only the due
  // candidates instead of walking every open cell.
  private final NavigableMap<Long, Set<Windowed<K>>> cellsByWindowEnd = new TreeMap<>();
  private final Set<Windowed<K>> changedSinceFlush = new HashSet<>();
  // Non-final: detachCheckpointDelta hands the sets over and installs fresh replacements.
  private Set<Windowed<K>> changedSinceCheckpoint = new HashSet<>();
  private Set<Windowed<K>> evictedSinceCheckpoint = new HashSet<>();

  /**
   * The cell's accumulator, or {@code null} if the cell is not open. Probe-safe (see class doc).
   */
  ACC get(final Windowed<K> cell) {
    return open.get(cell);
  }

  /** Upserts the cell's accumulator; a <em>new</em> cell's key must be owned by the caller. */
  void put(final Windowed<K> cell, final ACC acc) {
    open.put(cell, acc);
  }

  boolean contains(final Windowed<K> cell) {
    return open.containsKey(cell);
  }

  boolean isEmpty() {
    return open.isEmpty();
  }

  void forEachOpen(final BiConsumer<Windowed<K>, ACC> consumer) {
    for (final Entry<Windowed<K>, ACC> cell : open.entrySet()) {
      consumer.accept(cell.getKey(), cell.getValue());
    }
  }

  /** Drops every open cell and all bookkeeping (the sealing side's segment cut). */
  void clear() {
    open.clear();
    cellsByWindowEnd.clear();
    changedSinceFlush.clear();
    changedSinceCheckpoint.clear();
    evictedSinceCheckpoint.clear();
  }

  /** Registers an open cell under its (constant) window end for due-window finalization. */
  void index(final Windowed<K> cell, final long windowEnd) {
    cellsByWindowEnd.computeIfAbsent(windowEnd, end -> new HashSet<>()).add(cell);
  }

  /** Marks the cell dirty for the next flush and checkpoint (and un-evicts a resurrected one). */
  void markChanged(final Windowed<K> cell) {
    changedSinceFlush.add(cell);
    changedSinceCheckpoint.add(cell);
    evictedSinceCheckpoint.remove(cell);
  }

  boolean isChangedSinceFlush(final Windowed<K> cell) {
    return changedSinceFlush.contains(cell);
  }

  boolean isChangedSinceCheckpoint(final Windowed<K> cell) {
    return changedSinceCheckpoint.contains(cell);
  }

  void forEachChangedSinceFlush(final BiConsumer<Windowed<K>, ACC> consumer) {
    for (final Windowed<K> cell : changedSinceFlush) {
      consumer.accept(cell, open.get(cell));
    }
  }

  void clearChangedSinceFlush() {
    changedSinceFlush.clear();
  }

  /**
   * Hands over the changed/evicted-since-checkpoint sets as one frozen checkpoint cut and installs
   * fresh empty trackers, so the owner keeps tracking new changes while the detached cut persists.
   * This state never touches the returned sets again.
   */
  CheckpointDelta<K> detachCheckpointDelta() {
    final CheckpointDelta<K> delta =
        new CheckpointDelta<>(changedSinceCheckpoint, evictedSinceCheckpoint);
    changedSinceCheckpoint = new HashSet<>();
    evictedSinceCheckpoint = new HashSet<>();
    return delta;
  }

  /**
   * Re-adds a detached (but never persisted) delta into the current tracking so the next cut
   * re-includes it. Union semantics where the current state wins and the detached delta only fills
   * gaps: a detached changed cell that was evicted after the detach stays evicted (it must not be
   * resurrected into the changed set), and a detached evicted cell that was re-created after the
   * detach stays changed (its durable row must not be deleted). The two current sets therefore stay
   * disjoint.
   */
  void mergeBackCheckpointDelta(final CheckpointDelta<K> delta) {
    for (final Windowed<K> cell : delta.changed()) {
      if (!evictedSinceCheckpoint.contains(cell)) {
        changedSinceCheckpoint.add(cell);
      }
    }
    for (final Windowed<K> cell : delta.evicted()) {
      if (!changedSinceCheckpoint.contains(cell) && !open.containsKey(cell)) {
        evictedSinceCheckpoint.add(cell);
      }
    }
  }

  /**
   * Walks the open cells whose window end is {@code <= maxWindowEndInclusive} in window-end order
   * and offers each to {@code visitor}; a cell the visitor finalizes (returns {@code true}, after
   * emitting its final value) is evicted — removed from the open map, the window-end index, and the
   * changed sets, and marked for durable deletion at the next checkpoint. Cells with a later window
   * end are untouched, so an idle call is O(1), not O(open cells).
   */
  void evictDue(final long maxWindowEndInclusive, final DueCellVisitor<K, ACC> visitor) {
    final Iterator<Entry<Long, Set<Windowed<K>>>> ends =
        cellsByWindowEnd.headMap(maxWindowEndInclusive, true).entrySet().iterator();
    while (ends.hasNext()) {
      final Entry<Long, Set<Windowed<K>>> entry = ends.next();
      final Iterator<Windowed<K>> cells = entry.getValue().iterator();
      while (cells.hasNext()) {
        final Windowed<K> cell = cells.next();
        if (visitor.tryFinalize(entry.getKey(), cell, open.get(cell))) {
          cells.remove(); // evict from the window-end index
          changedSinceFlush.remove(cell);
          changedSinceCheckpoint.remove(cell);
          evictedSinceCheckpoint.add(cell); // delete the durable cell at the next checkpoint
          open.remove(cell); // evict from the heap working set
        }
      }
      if (entry.getValue().isEmpty()) {
        ends.remove();
      }
    }
  }

  /** The owner's finalization policy, applied per due cell by {@link #evictDue}. */
  @FunctionalInterface
  interface DueCellVisitor<K, ACC> {

    /**
     * Decides whether the due cell finalizes now; if it does, the visitor emits the cell's final
     * value before returning {@code true}, and the state evicts the cell.
     */
    boolean tryFinalize(long windowEnd, Windowed<K> cell, ACC value);
  }

  /**
   * The changed/evicted cell sets of one checkpoint cut, handed over by {@link
   * #detachCheckpointDelta()}. The sets are disjoint and owned by the holder once detached.
   */
  record CheckpointDelta<K>(Set<Windowed<K>> changed, Set<Windowed<K>> evicted) {}
}
