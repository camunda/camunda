/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.segment;

import java.util.Objects;

/**
 * The single-threaded bump allocator freezing segments write through: hands out the chunk a slice
 * of a given length should be appended to, rotating to a fresh pooled chunk when the current one is
 * full and diverting oversized slices to dedicated right-sized chunks.
 *
 * <p>One writer is shared by all stores of a domain (they freeze on the same owner thread), so
 * consecutive small segments pack into the same chunk instead of each wasting a mostly-empty one.
 * The chunk-level consequence: a chunk holds bytes of several segments, and returns to the pool
 * only when <em>all</em> of them released — which persist rounds do wholesale, since a round drains
 * every store's segments together.
 *
 * <p>The writer holds one reference on its open chunk (dropped when it rotates past it), so a chunk
 * still being filled can never be recycled under the writer even after every segment written into
 * it retired. See {@link Chunk} for the full lifetime rule.
 *
 * <p><b>Threading:</b> owner thread only.
 */
public final class ChunkWriter {

  private final ChunkPool pool;
  private final int dedicatedThreshold;
  private Chunk current;

  public ChunkWriter(final ChunkPool pool) {
    this.pool = Objects.requireNonNull(pool, "pool");
    // slices above a quarter chunk go to dedicated chunks, bounding the tail waste a rotation
    // leaves behind to a quarter of the chunk size
    dedicatedThreshold = pool.chunkSize() / 4;
  }

  /**
   * The chunk the next {@code length}-byte slice should be {@link Chunk#append(byte[])}ed to.
   * Oversized slices get a dedicated chunk whose single reference the caller adopts; otherwise the
   * open pooled chunk is returned (rotated first if too full), on which the writer keeps its own
   * reference — the caller must retain it for every segment referencing it.
   */
  Chunk chunkFor(final int length) {
    if (length > dedicatedThreshold) {
      return pool.acquireDedicated(length);
    }
    if (current == null || current.remaining() < length) {
      final Chunk previous = current;
      current = pool.acquire();
      if (previous != null) {
        previous.release();
      }
    }
    return current;
  }
}
