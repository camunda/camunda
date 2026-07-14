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
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Merges {@code k} sorted entry streams into a single stream in unsigned-byte key order, emitting
 * exactly one entry per distinct key: the newest version's key, value and tombstone state, with
 * {@link Entry#flushed()} set to the OR over <em>every</em> version of that key across all inputs.
 *
 * <p>This is the streaming twin of {@link FlatSegment#merge(List, boolean)} (without absorption):
 * where {@link KWayMergeIterator} consumes shadowed versions silently — the read-view layering
 * rule, newest version's flags win as-is — this iterator folds the shadowed versions' flushed flags
 * into the emitted entry. The flushed property is sticky across versions: once any version of a key
 * was flushed, its final tombstone must reach the durable delegate, which is exactly the OR a
 * persist drain needs to decide put / delete / skip per key without materializing a merged segment
 * first.
 *
 * <p>Inputs are given in priority order: index 0 is the highest priority, i.e. the newest layer.
 * Each input stream must be strictly increasing in unsigned-byte key order (sorted, no duplicate
 * keys within one stream), as produced by {@link FlatSegment#range(byte[])}.
 *
 * <p>Not thread-safe; single-consumer, like the input iterators it wraps.
 */
public final class FlushedOrMergeIterator implements Iterator<Entry> {

  private final Iterator<Entry>[] streams;
  private final Entry[] heads;

  /**
   * @param newestFirst the sorted input streams, highest priority (newest layer) first
   */
  @SuppressWarnings("unchecked")
  public FlushedOrMergeIterator(final List<Iterator<Entry>> newestFirst) {
    streams = newestFirst.toArray(new Iterator[0]);
    heads = new Entry[streams.length];
    for (int i = 0; i < streams.length; i++) {
      advance(i);
    }
  }

  @Override
  public boolean hasNext() {
    for (final Entry head : heads) {
      if (head != null) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Entry next() {
    int winner = -1;
    for (int i = 0; i < heads.length; i++) {
      if (heads[i] != null
          && (winner < 0 || Arrays.compareUnsigned(heads[i].key(), heads[winner].key()) < 0)) {
        winner = i;
      }
    }
    if (winner < 0) {
      throw new NoSuchElementException();
    }
    final Entry newest = heads[winner];
    boolean flushed = false;
    for (int i = 0; i < heads.length; i++) {
      if (heads[i] != null && Arrays.compareUnsigned(heads[i].key(), newest.key()) == 0) {
        flushed |= heads[i].flushed();
        advance(i);
      }
    }
    if (flushed == newest.flushed()) {
      return newest;
    }
    return new Entry(newest.key(), newest.value(), true);
  }

  private void advance(final int index) {
    heads[index] = streams[index].hasNext() ? streams[index].next() : null;
  }
}
