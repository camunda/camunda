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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
 * <p><b>Threading and lifecycle:</b> safe to read from any thread. Views are reference-counted: the
 * creator owns one reference (the coordinator holds it for the current view), and each reader adds
 * one via {@link #retain()} — normally through {@link ViewPublisher#acquireLatest()} — and drops it
 * via {@link #release()} when done. A view retains its segments (keeping their chunk slices valid)
 * and owns one reference on its pinned snapshot; the last release drops both, so the snapshot
 * closes exactly once — when the last view holding it is itself done — and segment chunks recycle
 * only after every reader let go. A rotation therefore never pulls a snapshot or a segment's bytes
 * from under a reader mid-scan; conversely, a view must not be read after the caller's own release.
 * Double release throws. Everything reachable from a view is immutable.
 *
 * <p><b>Freshness:</b> a view reflects the state as of the last freeze it includes — readers act on
 * slightly stale data by design, and their output must round-trip as commands validated against
 * live state (which is exactly how the async schedulers already work).
 */
public final class ReadOnlyView {

  private final Map<String, List<FlatSegment>> segmentsNewestFirstByStore;
  private final RefCountedSnapshot snapshotRef;
  private final AtomicInteger references = new AtomicInteger(1);

  /** Wraps the snapshot in a fresh reference count owned by this view. */
  public ReadOnlyView(
      final Map<String, List<FlatSegment>> segmentsNewestFirstByStore,
      final ReadSnapshot snapshot) {
    this(segmentsNewestFirstByStore, new RefCountedSnapshot(snapshot));
  }

  /**
   * Takes ownership of one already-counted reference on {@code snapshotRef} — the caller must have
   * retained it for this view (or pass a freshly created count) — and retains every segment, which
   * requires the caller to still hold them alive (e.g. in the store pipelines they came from).
   */
  ReadOnlyView(
      final Map<String, List<FlatSegment>> segmentsNewestFirstByStore,
      final RefCountedSnapshot snapshotRef) {
    Objects.requireNonNull(segmentsNewestFirstByStore, "segmentsNewestFirstByStore");
    final Map<String, List<FlatSegment>> copy = new HashMap<>();
    segmentsNewestFirstByStore.forEach((name, segments) -> copy.put(name, List.copyOf(segments)));
    this.segmentsNewestFirstByStore = Map.copyOf(copy);
    this.snapshotRef = Objects.requireNonNull(snapshotRef, "snapshotRef");
    forEachSegment(FlatSegment::retain);
  }

  /** The visible value for {@code key} in store {@code storeName}, or null. */
  public byte[] get(final String storeName, final byte[] key) {
    for (final FlatSegment segment : segmentsOf(storeName)) {
      final int index = segment.indexOfKey(key);
      if (index >= 0) {
        return segment.valueAt(index); // null for a tombstone — which must not fall through
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
   * Adds one reference on this view, keeping its segments and pinned snapshot alive past the
   * coordinator's next rotation. Safe from any thread, but only race-free while the caller already
   * holds a reference (directly or via a publication lock); readers should acquire through {@link
   * ViewPublisher#acquireLatest()} instead of calling this directly.
   *
   * @throws IllegalStateException if the view was already released to zero
   */
  public void retain() {
    while (true) {
      final int current = references.get();
      if (current == 0) {
        throw new IllegalStateException(
            "expected a live view to retain, but it was already released to zero");
      }
      if (references.compareAndSet(current, current + 1)) {
        return;
      }
    }
  }

  /**
   * Drops one reference; the last release releases the view's segments (recycling chunks no other
   * holder references) and its snapshot reference (the pinned snapshot closes when the last view
   * holding it is done). The view must not be read after the caller's own release.
   *
   * @throws IllegalStateException on a double release
   */
  public void release() {
    while (true) {
      final int current = references.get();
      if (current == 0) {
        throw new IllegalStateException(
            "expected a live view to release, but it was already released to zero");
      }
      if (references.compareAndSet(current, current - 1)) {
        if (current == 1) {
          forEachSegment(FlatSegment::release);
          snapshotRef.release();
        }
        return;
      }
    }
  }

  private void forEachSegment(final Consumer<FlatSegment> action) {
    for (final List<FlatSegment> segments : segmentsNewestFirstByStore.values()) {
      segments.forEach(action);
    }
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
