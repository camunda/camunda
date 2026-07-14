/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.segment;

import io.camunda.zeebe.db.layered.Entry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An immutable, flattened snapshot of an active overlay: entries in unsigned-byte key order, their
 * key and value bytes bump-copied into pooled {@link Chunk}s at freeze time and referenced as
 * packed slices — binary-search point lookups and contiguous prefix ranges over parallel arrays,
 * without one {@code byte[]} per key/value pinned for the pipeline's lifetime. Frozen once at
 * construction, then never modified — which is the property that makes segments safe to hand across
 * threads (persist/merge IO thread, async reader views) without locks.
 *
 * <p><b>Lifetime:</b> segments are reference-counted. The creator (the store pipeline) owns the
 * initial reference; every other holder — a {@code ReadOnlyView}, most notably — pairs {@link
 * #retain()} with {@link #release()}. The last release drops the segment's references on its
 * backing chunks, which is what lets whole chunks return to the pool when a persist round retires
 * its segments (see {@link Chunk} for the full rule). Reading a segment without holding a reference
 * is undefined: its chunk bytes may already have been recycled.
 *
 * <p>The sorted layout doubles as a min/max key range: point lookups and prefix ranges first check
 * the probe against it and bail out on a miss for the cost of two compares — deep pipelines stay
 * cheap for keys most segments cannot hold.
 *
 * <p>Each segment carries a {@code watermark}: the highest log position whose effects it contains,
 * stamped at freeze time. A persist round that drains segments writes the newest drained watermark
 * as the recovery anchor, atomically with the entries.
 *
 * <p>{@link #merge(List, boolean)} collapses a pipeline run into one segment, newest version per
 * key winning. The merge is <em>index-only</em>: the merged segment references the surviving
 * entries' existing chunk slices — refs move, bytes don't — so merging costs no byte copying on any
 * thread and is safe wherever the inputs stay retained (the pipeline retains captured runs until
 * the merge is swapped in). The flip side: bytes of superseded and absorbed entries stay pinned in
 * their chunks until every referencing segment retires, i.e. until the next successful persist
 * round — accounted byte sizes track live entries only, as before. With {@code absorbAnnihilated},
 * a key whose final version is a tombstone and which was never flushed (no merged version has
 * {@link Entry#flushed()}) is dropped entirely: the put/delete pair annihilates in memory and never
 * costs a durable write. This is sound only under the caller's guarantee that deleted keys are
 * never read again (deletes are garbage collection, not semantics) — see {@link
 * io.camunda.zeebe.db.layered.LayeredKeyValueStore}.
 */
public final class FlatSegment {

  private static final byte[] EMPTY = new byte[0];

  // packed slice reference: chunk index (16 bits) | offset (24 bits) | length (24 bits);
  // TOMBSTONE_REF marks a buffered delete, EMPTY_REF a genuine zero-length slice
  private static final long TOMBSTONE_REF = -1L;
  private static final long EMPTY_REF = 0L;
  private static final int OFFSET_BITS = 24;
  private static final int LENGTH_BITS = 24;
  private static final int SLICE_MASK = (1 << LENGTH_BITS) - 1;
  private static final int MAX_CHUNKS = 1 << (Long.SIZE - OFFSET_BITS - LENGTH_BITS);

  private final Chunk[] chunks;
  private final long[] keyRefs;
  private final long[] valueRefs; // TOMBSTONE_REF iff tombstone
  private final boolean[] flushed;
  private final long watermark;
  private final long byteSize;
  private final SegmentReferences references = new SegmentReferences();

  /** Adopts one already-counted reference per distinct chunk in {@code chunks}. */
  private FlatSegment(
      final Chunk[] chunks,
      final long[] keyRefs,
      final long[] valueRefs,
      final boolean[] flushed,
      final long watermark,
      final long byteSize) {
    this.chunks = chunks;
    this.keyRefs = keyRefs;
    this.valueRefs = valueRefs;
    this.flushed = flushed;
    this.watermark = watermark;
    this.byteSize = byteSize;
  }

  /**
   * Flattens a sorted overlay into a segment, copying every key and value into chunks handed out by
   * {@code writer} (the one byte copy of an entry's buffered lifetime — staging and active hold
   * plain arrays, everything frozen lives in chunks). The map must be sorted in unsigned-byte key
   * order; entry values follow {@link Entry} semantics (null value = tombstone). The caller owns
   * the returned segment's initial reference.
   */
  public static FlatSegment of(
      final NavigableMap<byte[], Entry> sorted, final long watermark, final ChunkWriter writer) {
    final int n = sorted.size();
    final long[] keyRefs = new long[n];
    final long[] valueRefs = new long[n];
    final boolean[] flushed = new boolean[n];
    final ChunkTable chunks = new ChunkTable();
    long byteSize = 0;
    int i = 0;
    for (final Entry entry : sorted.values()) {
      keyRefs[i] = chunks.copyIn(writer, entry.key());
      byteSize += entry.key().length;
      if (entry.tombstone()) {
        valueRefs[i] = TOMBSTONE_REF;
      } else {
        valueRefs[i] = chunks.copyIn(writer, entry.value());
        byteSize += entry.value().length;
      }
      flushed[i] = entry.flushed();
      i++;
    }
    return new FlatSegment(chunks.toArray(), keyRefs, valueRefs, flushed, watermark, byteSize);
  }

  /**
   * Merges segments into one, oldest first; a newer version of a key supersedes an older one. The
   * merged watermark is the newest input watermark. Index-only — see the class javadoc. Runs on any
   * thread as long as every input stays retained throughout; the caller owns the returned segment's
   * initial reference (its chunk references are taken here, while the inputs still pin the chunks).
   * With {@code absorbAnnihilated}, final tombstones of never-flushed keys are dropped (see class
   * javadoc).
   */
  public static FlatSegment merge(
      final List<FlatSegment> oldestFirst, final boolean absorbAnnihilated) {
    final int k = oldestFirst.size();
    final int[] cursors = new int[k];
    long watermark = -1;
    int capacity = 0;
    for (final FlatSegment segment : oldestFirst) {
      watermark = Math.max(watermark, segment.watermark);
      capacity += segment.entryCount();
    }
    final long[] mergedKeys = new long[capacity];
    final long[] mergedValues = new long[capacity];
    final boolean[] mergedFlushed = new boolean[capacity];
    final ChunkTable chunks = new ChunkTable();
    // per-input chunk index -> merged chunk index, filled on first use
    final int[][] remap = new int[k][];
    for (int i = 0; i < k; i++) {
      remap[i] = new int[oldestFirst.get(i).chunks.length];
      Arrays.fill(remap[i], -1);
    }
    long byteSize = 0;
    int size = 0;
    while (true) {
      // the smallest not-yet-consumed key across all inputs; its coordinates are captured before
      // the cursors advance so the equality compares below stay stable
      int minInput = -1;
      int minIndex = -1;
      for (int i = 0; i < k; i++) {
        final FlatSegment segment = oldestFirst.get(i);
        if (cursors[i] < segment.entryCount()
            && (minInput < 0
                || compareKeys(segment, cursors[i], oldestFirst.get(minInput), minIndex) < 0)) {
          minInput = i;
          minIndex = cursors[i];
        }
      }
      if (minInput < 0) {
        break;
      }
      // Walking oldest to newest, the last matching version's value wins; the flushed property is
      // sticky across versions: once any version of the key was flushed, its final tombstone must
      // reach the delegate.
      final FlatSegment minSegment = oldestFirst.get(minInput);
      int winnerInput = -1;
      int winnerIndex = -1;
      boolean wasFlushed = false;
      for (int i = 0; i < k; i++) {
        final FlatSegment segment = oldestFirst.get(i);
        final int cursor = cursors[i];
        if (cursor < segment.entryCount()
            && compareKeys(segment, cursor, minSegment, minIndex) == 0) {
          winnerInput = i;
          winnerIndex = cursor;
          wasFlushed |= segment.flushed[cursor];
          cursors[i]++;
        }
      }
      final FlatSegment winner = oldestFirst.get(winnerInput);
      final long valueRef = winner.valueRefs[winnerIndex];
      if (absorbAnnihilated && valueRef == TOMBSTONE_REF && !wasFlushed) {
        continue;
      }
      final long keyRef = winner.keyRefs[winnerIndex];
      mergedKeys[size] = chunks.adoptRef(winner, keyRef, remap[winnerInput]);
      mergedValues[size] =
          valueRef == TOMBSTONE_REF
              ? TOMBSTONE_REF
              : chunks.adoptRef(winner, valueRef, remap[winnerInput]);
      mergedFlushed[size] = wasFlushed;
      byteSize += lengthOf(keyRef) + (valueRef == TOMBSTONE_REF ? 0 : lengthOf(valueRef));
      size++;
    }
    if (size == capacity) {
      return new FlatSegment(
          chunks.toArray(), mergedKeys, mergedValues, mergedFlushed, watermark, byteSize);
    }
    return new FlatSegment(
        chunks.toArray(),
        Arrays.copyOf(mergedKeys, size),
        Arrays.copyOf(mergedValues, size),
        Arrays.copyOf(mergedFlushed, size),
        watermark,
        byteSize);
  }

  // ------------------------------------------------------------------
  // Point access
  // ------------------------------------------------------------------

  /**
   * The index of {@code key}'s entry, or a negative value if this segment has no version of it —
   * for allocation-free access via {@link #tombstoneAt(int)}, {@link #flushedAt(int)} and {@link
   * #valueAt(int)}.
   */
  public int indexOfKey(final byte[] key) {
    if (excludesKey(key)) {
      return -1;
    }
    int low = 0;
    int high = keyRefs.length - 1;
    while (low <= high) {
      final int mid = (low + high) >>> 1;
      final int compared = compareKeyAt(mid, key);
      if (compared < 0) {
        low = mid + 1;
      } else if (compared > 0) {
        high = mid - 1;
      } else {
        return mid;
      }
    }
    return -1;
  }

  /**
   * The entry for {@code key}, or {@code null} if this segment has no version of it. Materializes
   * key and value copies — hot paths that need only parts of an entry should pair {@link
   * #indexOfKey(byte[])} with the indexed accessors instead.
   */
  public Entry findEntry(final byte[] key) {
    final int index = indexOfKey(key);
    return index < 0 ? null : entryAt(index);
  }

  /** Whether the entry at {@code index} is a buffered delete. */
  public boolean tombstoneAt(final int index) {
    return valueRefs[index] == TOMBSTONE_REF;
  }

  /**
   * Whether any drained version of the entry at {@code index} reached (or reaches) the durable
   * store — see {@link Entry#flushed()}.
   */
  public boolean flushedAt(final int index) {
    return flushed[index];
  }

  /** A copy of the value at {@code index}, or {@code null} for a tombstone. */
  public byte[] valueAt(final int index) {
    final long ref = valueRefs[index];
    return ref == TOMBSTONE_REF ? null : slice(ref);
  }

  /** A copy of the key at {@code index}. */
  public byte[] keyAt(final int index) {
    return slice(keyRefs[index]);
  }

  // ------------------------------------------------------------------
  // Range access
  // ------------------------------------------------------------------

  /**
   * Iterates the entries whose key starts with {@code prefix}, in key order, materializing an
   * {@link Entry} (key and value copies) per step. Prefix-matching keys are contiguous in the
   * sorted arrays, so this is a binary search plus a linear walk over exactly the matches —
   * preceded by a min/max range check that rejects a foreign prefix for two bounded compares. An
   * empty prefix iterates everything.
   */
  public Iterator<Entry> range(final byte[] prefix) {
    if (excludesPrefix(prefix)) {
      return Collections.emptyIterator();
    }
    // lower bound: the first index whose key sorts at or above the prefix
    int low = 0;
    int high = keyRefs.length;
    while (low < high) {
      final int mid = (low + high) >>> 1;
      if (compareKeyAt(mid, prefix) < 0) {
        low = mid + 1;
      } else {
        high = mid;
      }
    }
    final int from = low;
    return new Iterator<>() {
      private int next = from;

      @Override
      public boolean hasNext() {
        return next < keyRefs.length && startsWithAt(next, prefix);
      }

      @Override
      public Entry next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        final Entry entry = entryAt(next);
        next++;
        return entry;
      }
    };
  }

  // ------------------------------------------------------------------
  // Lifecycle
  // ------------------------------------------------------------------

  /**
   * Adds one reference, keeping the segment's chunk slices valid for the caller. Safe from any
   * thread, but only race-free while the caller already holds a reference directly or through a
   * safe hand-off from a holder (views retain their segments while the pipeline still holds them).
   *
   * @throws IllegalStateException if the segment was already released to zero
   */
  public void retain() {
    references.retain();
  }

  /**
   * Drops one reference; the last release drops the segment's chunk references, recycling every
   * chunk no other segment (or the writer still filling it) references. The segment must not be
   * read after the caller's own release.
   *
   * @throws IllegalStateException on a release below zero
   */
  public void release() {
    if (references.release()) {
      for (final Chunk chunk : chunks) {
        chunk.release();
      }
    }
  }

  // ------------------------------------------------------------------
  // Plain accessors
  // ------------------------------------------------------------------

  public long watermark() {
    return watermark;
  }

  public int entryCount() {
    return keyRefs.length;
  }

  /** The live entry bytes (key + value lengths) this segment accounts for — not chunk capacity. */
  public long byteSize() {
    return byteSize;
  }

  public boolean isEmpty() {
    return keyRefs.length == 0;
  }

  // ------------------------------------------------------------------
  // Slice plumbing
  // ------------------------------------------------------------------

  private Entry entryAt(final int index) {
    return new Entry(keyAt(index), valueAt(index), flushed[index]);
  }

  private byte[] slice(final long ref) {
    final int length = lengthOf(ref);
    if (length == 0) {
      return EMPTY;
    }
    final int offset = offsetOf(ref);
    return Arrays.copyOfRange(chunks[chunkIndexOf(ref)].bytes(), offset, offset + length);
  }

  private int compareKeyAt(final int index, final byte[] probe) {
    final long ref = keyRefs[index];
    final byte[] backing = backingOf(ref);
    final int offset = offsetOf(ref);
    return Arrays.compareUnsigned(backing, offset, offset + lengthOf(ref), probe, 0, probe.length);
  }

  private static int compareKeys(
      final FlatSegment left, final int leftIndex, final FlatSegment right, final int rightIndex) {
    final long leftRef = left.keyRefs[leftIndex];
    final long rightRef = right.keyRefs[rightIndex];
    final byte[] leftBacking = left.backingOf(leftRef);
    final byte[] rightBacking = right.backingOf(rightRef);
    final int leftOffset = offsetOf(leftRef);
    final int rightOffset = offsetOf(rightRef);
    return Arrays.compareUnsigned(
        leftBacking,
        leftOffset,
        leftOffset + lengthOf(leftRef),
        rightBacking,
        rightOffset,
        rightOffset + lengthOf(rightRef));
  }

  private boolean startsWithAt(final int index, final byte[] prefix) {
    final long ref = keyRefs[index];
    if (lengthOf(ref) < prefix.length) {
      return false;
    }
    final byte[] backing = backingOf(ref);
    final int offset = offsetOf(ref);
    return Arrays.equals(backing, offset, offset + prefix.length, prefix, 0, prefix.length);
  }

  /** Whether {@code key} lies outside this segment's min/max key range — two compares. */
  private boolean excludesKey(final byte[] key) {
    return keyRefs.length == 0
        || compareKeyAt(0, key) > 0
        || compareKeyAt(keyRefs.length - 1, key) < 0;
  }

  /**
   * Whether no key of this segment can start with {@code prefix}. Truncating a key to the prefix
   * length is weakly order-preserving, so if even the largest key truncates below the prefix, or
   * the smallest key truncates above it, the prefix range and the segment's min/max range are
   * disjoint — two bounded compares, no allocation.
   */
  private boolean excludesPrefix(final byte[] prefix) {
    if (keyRefs.length == 0) {
      return true;
    }
    if (prefix.length == 0) {
      return false;
    }
    return truncatedCompareAt(keyRefs.length - 1, prefix) < 0 || truncatedCompareAt(0, prefix) > 0;
  }

  /** Compares the key at {@code index}, truncated to the prefix length, against the prefix. */
  private int truncatedCompareAt(final int index, final byte[] prefix) {
    final long ref = keyRefs[index];
    final byte[] backing = backingOf(ref);
    final int offset = offsetOf(ref);
    final int length = Math.min(lengthOf(ref), prefix.length);
    return Arrays.compareUnsigned(backing, offset, offset + length, prefix, 0, prefix.length);
  }

  /** The array backing {@code ref}'s slice; the shared empty array for zero-length slices. */
  private byte[] backingOf(final long ref) {
    return lengthOf(ref) == 0 ? EMPTY : chunks[chunkIndexOf(ref)].bytes();
  }

  private static long ref(final int chunkIndex, final int offset, final int length) {
    return ((long) chunkIndex << (OFFSET_BITS + LENGTH_BITS))
        | ((long) offset << LENGTH_BITS)
        | length;
  }

  private static int chunkIndexOf(final long ref) {
    return (int) (ref >>> (OFFSET_BITS + LENGTH_BITS));
  }

  private static int offsetOf(final long ref) {
    return (int) ((ref >>> LENGTH_BITS) & SLICE_MASK);
  }

  private static int lengthOf(final long ref) {
    return (int) (ref & SLICE_MASK);
  }

  /**
   * The distinct chunks a segment under construction references, with the reference bookkeeping of
   * both build paths: a freshly copied-in slice retains the writer's pooled chunk (or adopts a
   * dedicated chunk's creation reference), an adopted ref from an index-only merge retains the
   * input's chunk on first use. The finished array's references transfer to the segment.
   */
  private static final class ChunkTable {

    private final ArrayList<Chunk> chunks = new ArrayList<>(2);

    /** Copies {@code src} into a chunk from {@code writer} and returns the packed slice ref. */
    long copyIn(final ChunkWriter writer, final byte[] src) {
      if (src.length == 0) {
        return EMPTY_REF;
      }
      final Chunk chunk = writer.chunkFor(src.length);
      final int offset = chunk.append(src);
      int index = indexOf(chunk);
      if (index < 0) {
        // a dedicated chunk's creation reference is adopted; a pooled chunk is additionally
        // retained (the writer keeps its own reference until it rotates past the chunk)
        if (!chunk.isDedicated()) {
          chunk.retain();
        }
        index = add(chunk);
      }
      return ref(index, offset, src.length);
    }

    /**
     * Re-points {@code ref} from {@code source}'s chunk table to this one, retaining on first use.
     */
    long adoptRef(final FlatSegment source, final long ref, final int[] sourceRemap) {
      if (lengthOf(ref) == 0) {
        return EMPTY_REF;
      }
      final int sourceChunk = chunkIndexOf(ref);
      int mapped = sourceRemap[sourceChunk];
      if (mapped < 0) {
        final Chunk chunk = source.chunks[sourceChunk];
        // safe: the input segment stays retained throughout the merge, so its chunk count is >= 1
        chunk.retain();
        mapped = add(chunk);
        sourceRemap[sourceChunk] = mapped;
      }
      return ref(mapped, offsetOf(ref), lengthOf(ref));
    }

    private int indexOf(final Chunk chunk) {
      // newest-first: consecutive appends cluster on the writer's current chunk
      for (int i = chunks.size() - 1; i >= 0; i--) {
        if (chunks.get(i) == chunk) {
          return i;
        }
      }
      return -1;
    }

    private int add(final Chunk chunk) {
      if (chunks.size() == MAX_CHUNKS) {
        throw new IllegalStateException(
            "expected a segment to reference at most %d chunks".formatted(MAX_CHUNKS));
      }
      chunks.add(chunk);
      return chunks.size() - 1;
    }

    Chunk[] toArray() {
      return chunks.toArray(new Chunk[0]);
    }
  }

  /** The segment's reference count; separated out to keep the counting logic in one place. */
  private static final class SegmentReferences {

    private final AtomicInteger count = new AtomicInteger(1);

    void retain() {
      while (true) {
        final int current = count.get();
        if (current == 0) {
          throw new IllegalStateException(
              "expected a live segment to retain, but it was already released to zero");
        }
        if (count.compareAndSet(current, current + 1)) {
          return;
        }
      }
    }

    /** True exactly once: for the release that dropped the count to zero. */
    boolean release() {
      while (true) {
        final int current = count.get();
        if (current == 0) {
          throw new IllegalStateException(
              "expected a live segment to release, but it was already released to zero");
        }
        if (count.compareAndSet(current, current - 1)) {
          return current == 1;
        }
      }
    }
  }
}
