/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.segment;

import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * One contiguous, heap-backed slab of frozen entry bytes (MSLAB-style): key and value bytes of
 * {@link FlatSegment}s are bump-appended into chunks instead of living in one {@code byte[]} per
 * key/value, so the long-lived buffered state consists of a handful of large arrays rather than
 * millions of small ones — the difference between a tenured region the collector walks in one step
 * and an old generation full of tiny objects.
 *
 * <p><b>Lifetime rule:</b> a slice into a chunk is valid exactly while a {@link FlatSegment}
 * referencing the chunk is retained. Every segment holds one reference per distinct chunk its
 * entries point into (taken at segment construction, dropped on the segment's last {@link
 * FlatSegment#release()}), and every holder of a segment — the store pipeline, each {@code
 * ReadOnlyView}, the {@link ChunkWriter} for the chunk it is still filling — retains what it holds.
 * A pooled chunk returns to its {@link ChunkPool} only when the last reference drops, so a reader
 * that follows the retain/release discipline can never observe recycled bytes. A leaked reference
 * degrades gracefully: the chunk is simply never reused and falls to the garbage collector with its
 * last holder — never the other way around.
 *
 * <p><b>Threading:</b> {@link #append(byte[])} and {@link #remaining()} are single-writer — only
 * the owner thread building segments calls them, and only until the writer moves past the chunk.
 * {@link #retain()} and {@link #release()} are safe from any thread (segments are released by
 * whichever reader drops the last view reference). Readers of already-frozen slices and the writer
 * appending beyond them touch disjoint regions of the backing array, and every reader receives its
 * segment through a safe-publication hand-off, so the bytes a segment references are visible to it.
 */
final class Chunk {

  private final ChunkPool pool; // null for a dedicated (never pooled) chunk
  private final UnsafeBuffer buffer;
  private final AtomicInteger references = new AtomicInteger(1);
  private int writeOffset;

  private Chunk(final ChunkPool pool, final int capacity) {
    this.pool = pool;
    buffer = new UnsafeBuffer(new byte[capacity]);
  }

  /** A pool-managed chunk; recycled into {@code pool} when the last reference drops. */
  static Chunk pooled(final ChunkPool pool, final int capacity) {
    return new Chunk(pool, capacity);
  }

  /**
   * A right-sized chunk for one oversized slice; never pooled — the last release simply abandons it
   * to the garbage collector.
   */
  static Chunk dedicated(final int capacity) {
    return new Chunk(null, capacity);
  }

  /** Bump-appends {@code src} and returns the offset it was written at. Owner thread only. */
  int append(final byte[] src) {
    final int offset = writeOffset;
    buffer.putBytes(offset, src);
    writeOffset = offset + src.length;
    return offset;
  }

  /** The bytes still unclaimed by {@link #append(byte[])}. Owner thread only. */
  int remaining() {
    return buffer.capacity() - writeOffset;
  }

  /**
   * The backing heap array, for allocation-free slice compares and copies. Chunks are always
   * heap-backed, so this is never null.
   */
  byte[] bytes() {
    return buffer.byteArray();
  }

  boolean isDedicated() {
    return pool == null;
  }

  /**
   * Adds one reference. Only race-free while the caller already holds one (a segment retains its
   * chunks while the inputs it merges — or the writer that filled it — still hold theirs).
   *
   * @throws IllegalStateException if the count already dropped to zero
   */
  void retain() {
    while (true) {
      final int current = references.get();
      if (current == 0) {
        throw new IllegalStateException(
            "expected a live chunk to retain, but it was already released to zero");
      }
      if (references.compareAndSet(current, current + 1)) {
        return;
      }
    }
  }

  /**
   * Drops one reference; the last release recycles a pooled chunk (or abandons a dedicated one).
   * Safe from any thread.
   *
   * @throws IllegalStateException on a release below zero (always a caller bug worth surfacing)
   */
  void release() {
    while (true) {
      final int current = references.get();
      if (current == 0) {
        throw new IllegalStateException(
            "expected a live chunk to release, but it was already released to zero");
      }
      if (references.compareAndSet(current, current - 1)) {
        if (current == 1 && pool != null) {
          // sole owner now: reset for the next acquirer, then hand back; the pool's queue is the
          // safe-publication point for the reset state
          writeOffset = 0;
          references.set(1);
          pool.recycle(this);
        }
        return;
      }
    }
  }
}
