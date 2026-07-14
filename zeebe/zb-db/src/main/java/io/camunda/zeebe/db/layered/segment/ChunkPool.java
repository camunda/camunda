/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.segment;

import org.agrona.concurrent.ManyToManyConcurrentArrayQueue;

/**
 * A bounded free list of fixed-size {@link Chunk}s, shared by all layered stores of one database:
 * persist rounds retire whole chunks at once (see the lifetime rule on {@link Chunk}), and the next
 * freezes reuse them instead of re-allocating multi-megabyte arrays every round.
 *
 * <p>The pool holds no registry of live chunks — only the free list. Acquiring past the pooled
 * supply allocates a fresh chunk, recycling past the bound drops the chunk for the garbage
 * collector; both degrade allocation reuse, never correctness.
 *
 * <p><b>Threading:</b> safe from any thread. Chunks are acquired on owner threads (freezes) but
 * recycled by whichever thread drops the last segment reference — possibly a view reader.
 */
public final class ChunkPool {

  /**
   * Default chunk capacity. Large enough that a busy store's freeze output occupies few chunks,
   * small enough that the tail waste of a slow domain's open chunk stays negligible.
   */
  public static final int DEFAULT_CHUNK_SIZE = 2 * 1024 * 1024;

  /**
   * Default bound on retained free chunks (with {@link #DEFAULT_CHUNK_SIZE}: 32 MiB per pool). A
   * pool bigger than a persist round's typical chunk turnover buys nothing; overflow chunks are
   * dropped for the garbage collector.
   */
  public static final int DEFAULT_MAX_POOLED_CHUNKS = 16;

  /**
   * The hard cap on chunk capacity and on a single key/value slice, from the 24-bit offset/length
   * encoding of {@link FlatSegment} entry references. Far above Zeebe's maximum record size.
   */
  static final int MAX_SLICE_BYTES = (1 << 24) - 1;

  private final int chunkSize;
  private final ManyToManyConcurrentArrayQueue<Chunk> free;

  public ChunkPool() {
    this(DEFAULT_CHUNK_SIZE, DEFAULT_MAX_POOLED_CHUNKS);
  }

  public ChunkPool(final int chunkSize, final int maxPooledChunks) {
    if (chunkSize <= 0 || chunkSize > MAX_SLICE_BYTES) {
      throw new IllegalArgumentException(
          "expected chunkSize in (0, %d], but was %d".formatted(MAX_SLICE_BYTES, chunkSize));
    }
    if (maxPooledChunks < 1) {
      throw new IllegalArgumentException(
          "expected maxPooledChunks to be at least 1, but was " + maxPooledChunks);
    }
    this.chunkSize = chunkSize;
    free = new ManyToManyConcurrentArrayQueue<>(maxPooledChunks);
  }

  /** A pooled chunk — reused if the free list has one, freshly allocated otherwise. */
  Chunk acquire() {
    final Chunk recycled = free.poll();
    return recycled != null ? recycled : Chunk.pooled(this, chunkSize);
  }

  /** A right-sized, never-pooled chunk for one slice larger than the dedicated threshold. */
  Chunk acquireDedicated(final int capacity) {
    if (capacity > MAX_SLICE_BYTES) {
      throw new IllegalArgumentException(
          "expected a slice of at most %d bytes, but was %d — larger values cannot be buffered"
              .formatted(MAX_SLICE_BYTES, capacity));
    }
    return Chunk.dedicated(capacity);
  }

  /** Returns a fully released pooled chunk to the free list; drops it when the list is full. */
  void recycle(final Chunk chunk) {
    free.offer(chunk);
  }

  int chunkSize() {
    return chunkSize;
  }

  /** The number of chunks currently waiting for reuse (observability and tests). */
  public int pooledChunkCount() {
    return free.size();
  }
}
