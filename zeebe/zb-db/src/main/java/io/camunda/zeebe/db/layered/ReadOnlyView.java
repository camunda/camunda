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
import java.util.Map;
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
 * <p><b>Threading:</b> safe to read from any thread. A reader holds exactly one view and swaps it
 * when the coordinator publishes a fresh one; the view's snapshot is closed by the rotation, not by
 * the reader. Everything reachable from a view is immutable.
 *
 * <p><b>Freshness:</b> a view reflects the state as of the last freeze it includes — readers act on
 * slightly stale data by design, and their output must round-trip as commands validated against
 * live state (which is exactly how the async schedulers already work).
 */
public final class ReadOnlyView {

  public ReadOnlyView(
      final Map<String, List<FlatSegment>> segmentsNewestFirstByStore,
      final ReadSnapshot snapshot) {
    throw new UnsupportedOperationException("implemented in task #6");
  }

  /** The visible value for {@code key} in store {@code storeName}, or null. */
  public byte[] get(final String storeName, final byte[] key) {
    throw new UnsupportedOperationException("implemented in task #6");
  }

  public boolean exists(final String storeName, final byte[] key) {
    throw new UnsupportedOperationException("implemented in task #6");
  }

  /**
   * Visits every visible entry of store {@code storeName} whose key starts with {@code prefix}, in
   * unsigned-byte key order — segments newest-first merged with the pinned snapshot's stream,
   * tombstones hiding lower layers.
   */
  public void prefixScan(
      final String storeName, final byte[] prefix, final BiConsumer<byte[], byte[]> visitor) {
    throw new UnsupportedOperationException("implemented in task #6");
  }

  /** The pinned snapshot, exposed for the coordinator's rotation. Readers never close it. */
  ReadSnapshot snapshot() {
    throw new UnsupportedOperationException("implemented in task #6");
  }
}
