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
 * Merges {@code k} sorted entry streams into a single stream in unsigned-byte key order.
 *
 * <p>Inputs are given in priority order: index 0 is the highest priority, i.e. the newest layer.
 * When several streams carry a version of the same key, the entry from the lowest-index stream is
 * returned as-is, and the shadowed versions in lower-priority streams are consumed and skipped.
 * This is the layering rule of a merged read view: a newer layer's version of a key — value or
 * tombstone — hides every older version.
 *
 * <p>Each input stream must be strictly increasing in unsigned-byte key order (sorted, no duplicate
 * keys within one stream), as produced by {@link FlatSegment#range(byte[])}.
 *
 * <p>Not thread-safe; single-consumer, like the input iterators it wraps.
 */
public final class KWayMergeIterator implements Iterator<Entry> {

  private final Iterator<Entry>[] streams;
  private final Entry[] heads;

  /**
   * @param newestFirst the sorted input streams, highest priority (newest layer) first
   */
  @SuppressWarnings("unchecked")
  public KWayMergeIterator(final List<Iterator<Entry>> newestFirst) {
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
    final Entry entry = heads[winner];
    for (int i = 0; i < heads.length; i++) {
      if (heads[i] != null && Arrays.compareUnsigned(heads[i].key(), entry.key()) == 0) {
        advance(i);
      }
    }
    return entry;
  }

  private void advance(final int index) {
    heads[index] = streams[index].hasNext() ? streams[index].next() : null;
  }
}
