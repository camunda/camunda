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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableMap;
import java.util.NoSuchElementException;

/**
 * An immutable, flattened snapshot of an active overlay: entries in unsigned-byte key order, stored
 * as parallel arrays for binary-search point lookups and contiguous prefix ranges. Frozen once at
 * construction, then never modified — which is the single property that makes segments safe to hand
 * across threads (persist IO thread, async reader views) without locks.
 *
 * <p>The sorted layout doubles as a min/max key range ({@code keys[0]} … {@code keys[n-1]}): point
 * lookups and prefix ranges first check the probe against it and bail out on a miss for the cost of
 * two compares — deep pipelines stay cheap for keys most segments cannot hold.
 *
 * <p>Each segment carries a {@code watermark}: the highest log position whose effects it contains,
 * stamped at freeze time. A persist round that drains segments writes the newest drained watermark
 * as the recovery anchor, atomically with the entries.
 *
 * <p>{@link #merge(List, boolean)} collapses a pipeline run into one segment, newest version per
 * key winning. With {@code absorbAnnihilated}, a key whose final version is a tombstone and which
 * was never flushed (no merged version has {@link Entry#flushed()}) is dropped entirely: the
 * put/delete pair annihilates in memory and never costs a durable write. This is sound only under
 * the caller's guarantee that deleted keys are never read again (deletes are garbage collection,
 * not semantics) — see {@link io.camunda.zeebe.db.layered.LayeredKeyValueStore}.
 */
public final class FlatSegment {

  private final byte[][] keys;
  private final byte[][] values; // values[i] == null iff tombstone
  private final boolean[] flushed;
  private final long watermark;
  private final long byteSize;

  private FlatSegment(
      final byte[][] keys, final byte[][] values, final boolean[] flushed, final long watermark) {
    this.keys = keys;
    this.values = values;
    this.flushed = flushed;
    this.watermark = watermark;
    long bytes = 0;
    for (int i = 0; i < keys.length; i++) {
      bytes += keys[i].length + (values[i] == null ? 0 : values[i].length);
    }
    byteSize = bytes;
  }

  /**
   * Flattens a sorted overlay into a segment. The map must be sorted in unsigned-byte key order;
   * entry values follow {@link Entry} semantics (null value = tombstone).
   */
  public static FlatSegment of(final NavigableMap<byte[], Entry> sorted, final long watermark) {
    final int n = sorted.size();
    final byte[][] keys = new byte[n][];
    final byte[][] values = new byte[n][];
    final boolean[] flushed = new boolean[n];
    int i = 0;
    for (final Entry entry : sorted.values()) {
      keys[i] = entry.key();
      values[i] = entry.value();
      flushed[i] = entry.flushed();
      i++;
    }
    return new FlatSegment(keys, values, flushed, watermark);
  }

  /**
   * Merges segments into one, oldest first; a newer version of a key supersedes an older one. The
   * merged watermark is the newest input watermark. With {@code absorbAnnihilated}, final
   * tombstones of never-flushed keys are dropped (see class javadoc).
   */
  public static FlatSegment merge(
      final List<FlatSegment> oldestFirst, final boolean absorbAnnihilated) {
    final int k = oldestFirst.size();
    final int[] cursors = new int[k];
    long watermark = -1;
    int capacity = 0;
    for (final FlatSegment segment : oldestFirst) {
      watermark = Math.max(watermark, segment.watermark);
      capacity += segment.keys.length;
    }
    final byte[][] mergedKeys = new byte[capacity][];
    final byte[][] mergedValues = new byte[capacity][];
    final boolean[] mergedFlushed = new boolean[capacity];
    int size = 0;
    while (true) {
      byte[] minKey = null;
      for (int i = 0; i < k; i++) {
        final FlatSegment segment = oldestFirst.get(i);
        if (cursors[i] < segment.keys.length) {
          final byte[] key = segment.keys[cursors[i]];
          if (minKey == null || Arrays.compareUnsigned(key, minKey) < 0) {
            minKey = key;
          }
        }
      }
      if (minKey == null) {
        break;
      }
      // Walking oldest to newest, the last matching version's value wins; the flushed
      // property is sticky across versions: once any version of the key was flushed, its
      // final tombstone must reach the delegate.
      byte[] value = null;
      boolean wasFlushed = false;
      for (int i = 0; i < k; i++) {
        final FlatSegment segment = oldestFirst.get(i);
        final int cursor = cursors[i];
        if (cursor < segment.keys.length
            && Arrays.compareUnsigned(segment.keys[cursor], minKey) == 0) {
          value = segment.values[cursor];
          wasFlushed |= segment.flushed[cursor];
          cursors[i]++;
        }
      }
      if (absorbAnnihilated && value == null && !wasFlushed) {
        continue;
      }
      mergedKeys[size] = minKey;
      mergedValues[size] = value;
      mergedFlushed[size] = wasFlushed;
      size++;
    }
    if (size == capacity) {
      return new FlatSegment(mergedKeys, mergedValues, mergedFlushed, watermark);
    }
    return new FlatSegment(
        Arrays.copyOf(mergedKeys, size),
        Arrays.copyOf(mergedValues, size),
        Arrays.copyOf(mergedFlushed, size),
        watermark);
  }

  /** The entry for {@code key}, or {@code null} if this segment has no version of it. */
  public Entry findEntry(final byte[] key) {
    if (excludesKey(key)) {
      return null;
    }
    final int index = Arrays.binarySearch(keys, key, Arrays::compareUnsigned);
    if (index < 0) {
      return null;
    }
    return new Entry(keys[index], values[index], flushed[index]);
  }

  /** Whether {@code key} lies outside this segment's min/max key range — two compares. */
  private boolean excludesKey(final byte[] key) {
    return keys.length == 0
        || Arrays.compareUnsigned(key, keys[0]) < 0
        || Arrays.compareUnsigned(key, keys[keys.length - 1]) > 0;
  }

  /**
   * Iterates the entries whose key starts with {@code prefix}, in key order. Prefix-matching keys
   * are contiguous in the sorted arrays, so this is a binary search plus a linear walk over exactly
   * the matches — preceded by a min/max range check that rejects a foreign prefix for two bounded
   * compares. An empty prefix iterates everything.
   */
  public Iterator<Entry> range(final byte[] prefix) {
    if (excludesPrefix(prefix)) {
      return Collections.emptyIterator();
    }
    int start = Arrays.binarySearch(keys, prefix, Arrays::compareUnsigned);
    if (start < 0) {
      start = -start - 1;
    }
    final int from = start;
    return new Iterator<>() {
      private int next = from;

      @Override
      public boolean hasNext() {
        return next < keys.length && startsWith(keys[next], prefix);
      }

      @Override
      public Entry next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        final Entry entry = new Entry(keys[next], values[next], flushed[next]);
        next++;
        return entry;
      }
    };
  }

  /**
   * Whether no key of this segment can start with {@code prefix}. Truncating a key to the prefix
   * length is weakly order-preserving, so if even the largest key truncates below the prefix, or
   * the smallest key truncates above it, the prefix range and the segment's min/max range are
   * disjoint — two bounded compares, no allocation.
   */
  private boolean excludesPrefix(final byte[] prefix) {
    if (keys.length == 0) {
      return true;
    }
    if (prefix.length == 0) {
      return false;
    }
    final byte[] min = keys[0];
    final byte[] max = keys[keys.length - 1];
    return Arrays.compareUnsigned(
                max, 0, Math.min(max.length, prefix.length), prefix, 0, prefix.length)
            < 0
        || Arrays.compareUnsigned(
                min, 0, Math.min(min.length, prefix.length), prefix, 0, prefix.length)
            > 0;
  }

  private static boolean startsWith(final byte[] key, final byte[] prefix) {
    if (key.length < prefix.length) {
      return false;
    }
    return Arrays.equals(key, 0, prefix.length, prefix, 0, prefix.length);
  }

  public long watermark() {
    return watermark;
  }

  public int entryCount() {
    return keys.length;
  }

  public long byteSize() {
    return byteSize;
  }

  public boolean isEmpty() {
    return keys.length == 0;
  }
}
