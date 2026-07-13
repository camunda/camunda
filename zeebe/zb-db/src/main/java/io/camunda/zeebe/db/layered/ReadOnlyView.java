/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

import io.camunda.zeebe.db.layered.segment.FlatSegment;
import io.camunda.zeebe.db.layered.segment.KWayMergeIterator;
import io.camunda.zeebe.db.layered.segment.ShadowingZipper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * An immutable, point-in-time bundle handed to asynchronous readers (e.g. the timer due-date and
 * message-TTL checkers): each store's pipeline segments plus one pinned {@link ReadSnapshot} of the
 * durable state, taken at a matching cut. Reads resolve segments newest-first, then the snapshot —
 * never the mutable staging/active layers, which belong to the owner thread.
 *
 * <p>The pinned snapshot is what prevents the view from tearing while a persist burst runs
 * concurrently: without it, a reader could see a key created after its segments were frozen
 * (phantom — the burst already wrote it to the durable store) or miss a key its segments still show
 * live (ghost — the burst already deleted it durably). The invariant: <em>a view's snapshot must
 * predate the persist of every segment the view is missing.</em> The coordinator satisfies it by
 * rotating the snapshot only when a persist round completes, at the same instant the drained
 * segments leave new views.
 *
 * <p><b>Threading and lifecycle:</b> safe to read from any thread. Views are reference-counted
 * through their pinned snapshot: every view owns one reference for its own lifetime (taken at
 * construction), and each reader adds one via {@link #retain()} — normally through {@link
 * ViewPublisher#acquireLatest()} — and drops it via {@link #release()} when done. The snapshot
 * closes exactly once, when the last holder (coordinator or reader) releases; a rotation therefore
 * never pulls a snapshot from under a reader mid-scan. Double release throws. Everything reachable
 * from a view is immutable.
 *
 * <p><b>Freshness:</b> a view reflects the state as of the last freeze it includes — readers act on
 * slightly stale data by design, and their output must round-trip as commands validated against
 * live state (which is exactly how the async schedulers already work).
 */
public final class ReadOnlyView {

  private final Map<String, List<FlatSegment>> segmentsNewestFirstByStore;
  private final RefCountedSnapshot snapshotRef;

  /** Wraps the snapshot in a fresh reference count owned by this view. */
  public ReadOnlyView(
      final Map<String, List<FlatSegment>> segmentsNewestFirstByStore,
      final ReadSnapshot snapshot) {
    this(segmentsNewestFirstByStore, new RefCountedSnapshot(snapshot));
  }

  /**
   * Takes ownership of one already-counted reference on {@code snapshotRef} — the caller must have
   * retained it for this view (or pass a freshly created count).
   */
  ReadOnlyView(
      final Map<String, List<FlatSegment>> segmentsNewestFirstByStore,
      final RefCountedSnapshot snapshotRef) {
    Objects.requireNonNull(segmentsNewestFirstByStore, "segmentsNewestFirstByStore");
    final Map<String, List<FlatSegment>> copy = new HashMap<>();
    segmentsNewestFirstByStore.forEach((name, segments) -> copy.put(name, List.copyOf(segments)));
    this.segmentsNewestFirstByStore = Map.copyOf(copy);
    this.snapshotRef = Objects.requireNonNull(snapshotRef, "snapshotRef");
  }

  /** The visible value for {@code key} in store {@code storeName}, or null. */
  public byte[] get(final String storeName, final byte[] key) {
    for (final FlatSegment segment : segmentsOf(storeName)) {
      final Entry entry = segment.findEntry(key);
      if (entry != null) {
        return entry.value(); // null for a tombstone — which must not fall through
      }
    }
    return snapshotRef.snapshot().get(storeName, key);
  }

  public boolean exists(final String storeName, final byte[] key) {
    return get(storeName, key) != null;
  }

  /**
   * Visits every visible entry of store {@code storeName} whose key starts with {@code prefix}, in
   * unsigned-byte key order — segments newest-first merged with the pinned snapshot's stream,
   * tombstones hiding lower layers.
   */
  public void prefixScan(
      final String storeName, final byte[] prefix, final BiConsumer<byte[], byte[]> visitor) {
    final List<FlatSegment> segments = segmentsOf(storeName);
    final List<Iterator<Entry>> streams = new ArrayList<>(segments.size());
    for (final FlatSegment segment : segments) {
      streams.add(segment.range(prefix));
    }
    ShadowingZipper.merge(
        new KWayMergeIterator(streams),
        scan -> snapshotRef.snapshot().prefixScan(storeName, prefix, scan),
        visitor);
  }

  /**
   * Like {@link #prefixScan(String, byte[], BiConsumer)}, but the visitor may stop the scan early
   * by returning {@code false} — the counterpart of the owner path's {@code whileTrue} iteration
   * for view readers (e.g. a due-date scan that stops at the first not-yet-due entry). Early
   * termination uses a cached, stackless sentinel exception because the underlying push-based
   * streams cannot be stopped from a visitor.
   */
  public void prefixScanWhileTrue(
      final String storeName, final byte[] prefix, final ScanVisitor visitor) {
    try {
      prefixScan(
          storeName,
          prefix,
          (key, value) -> {
            if (!visitor.visit(key, value)) {
              throw StopVisitation.INSTANCE;
            }
          });
    } catch (final StopVisitation stop) {
      // the visitor requested termination; the scan state is local, nothing to unwind
    }
  }

  /**
   * Adds one reference on the view's pinned snapshot, keeping it alive past the coordinator's next
   * rotation. Safe from any thread, but see {@link RefCountedSnapshot#retain()} for the
   * caller-holds-a-reference precondition; readers should acquire through {@link
   * ViewPublisher#acquireLatest()} instead of calling this directly.
   *
   * @throws IllegalStateException if the snapshot was already released to zero
   */
  public void retain() {
    snapshotRef.retain();
  }

  /**
   * Drops one reference; the pinned snapshot closes when the last holder releases. The view must
   * not be read after the caller's own release.
   *
   * @throws IllegalStateException on a double release
   */
  public void release() {
    snapshotRef.release();
  }

  /** The shared snapshot count, exposed for the coordinator's rotation and republish. */
  RefCountedSnapshot snapshotRef() {
    return snapshotRef;
  }

  private List<FlatSegment> segmentsOf(final String storeName) {
    final List<FlatSegment> segments = segmentsNewestFirstByStore.get(storeName);
    if (segments == null) {
      throw new IllegalArgumentException(
          "Unknown store '%s'; known stores: %s"
              .formatted(storeName, segmentsNewestFirstByStore.keySet()));
    }
    return segments;
  }

  /** A scan visitor that may stop the iteration early by returning {@code false}. */
  @FunctionalInterface
  public interface ScanVisitor {
    boolean visit(byte[] key, byte[] value);
  }

  /** Cached, stackless control-flow sentinel — never observable outside this class. */
  private static final class StopVisitation extends RuntimeException {
    private static final StopVisitation INSTANCE = new StopVisitation();

    private StopVisitation() {
      super(null, null, false, false);
    }
  }
}
