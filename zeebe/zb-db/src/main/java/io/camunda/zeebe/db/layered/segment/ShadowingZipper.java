/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.segment;

import io.camunda.zeebe.db.layered.Entry;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Zips a pull-based overlay stream of {@link Entry} (typically a {@link KWayMergeIterator} over the
 * in-memory layers) with a push-based base stream of committed key/value pairs into a single
 * visitor callback, in unsigned-byte key order.
 *
 * <p>The overlay is authoritative: overlay entries with keys strictly smaller than the pushed key
 * are emitted first, an overlay entry on an equal key shadows the pushed pair, and once the base
 * stream ends the remaining overlay entries drain. Tombstone overlay entries are dropped at emit
 * time — they hide the base version of the key without being visited themselves.
 *
 * <p>Both streams must be strictly increasing in unsigned-byte key order (sorted, no duplicate
 * keys); a {@link KWayMergeIterator} already collapses duplicate keys across its input layers.
 */
public final class ShadowingZipper {

  private ShadowingZipper() {}

  /**
   * @param overlay the sorted overlay stream; on equal keys its entries shadow the base pairs
   * @param baseScan invokes the push-based base scan with the callback that receives its pairs
   * @param visitor receives the merged, visible pairs in unsigned-byte key order
   */
  public static void merge(
      final Iterator<Entry> overlay,
      final Consumer<BiConsumer<byte[], byte[]>> baseScan,
      final BiConsumer<byte[], byte[]> visitor) {
    final Lookahead pending = new Lookahead(overlay);
    baseScan.accept(
        (key, value) -> {
          while (pending.hasNext() && Arrays.compareUnsigned(pending.peekKey(), key) < 0) {
            emit(pending.next(), visitor);
          }
          if (pending.hasNext() && Arrays.compareUnsigned(pending.peekKey(), key) == 0) {
            emit(pending.next(), visitor); // any overlay version shadows the base one
          } else {
            visitor.accept(key, value);
          }
        });
    while (pending.hasNext()) {
      emit(pending.next(), visitor);
    }
  }

  private static void emit(final Entry entry, final BiConsumer<byte[], byte[]> visitor) {
    if (!entry.tombstone()) {
      visitor.accept(entry.key(), entry.value());
    }
  }

  /** A single-entry lookahead over the overlay, so the push-based base side can peek. */
  private static final class Lookahead {

    private final Iterator<Entry> entries;
    private Entry next;

    Lookahead(final Iterator<Entry> entries) {
      this.entries = entries;
      advance();
    }

    boolean hasNext() {
      return next != null;
    }

    byte[] peekKey() {
      return next.key();
    }

    Entry next() {
      final Entry current = next;
      advance();
      return current;
    }

    private void advance() {
      next = entries.hasNext() ? entries.next() : null;
    }
  }
}
