/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.segment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The chunk lifetime mechanics underneath frozen segments: a pooled chunk recycles exactly on its
 * last release (never under a holder), reuse resets the bump pointer, the pool is bounded, and
 * dedicated chunks never enter the pool.
 */
final class ChunkPoolTest {

  @Test
  void shouldRecycleOnlyOnLastRelease() {
    // given -- an acquired chunk with a second holder (as a segment referencing it would be)
    final ChunkPool pool = new ChunkPool(64, 4);
    final Chunk chunk = pool.acquire();
    chunk.retain();

    // when -- the first holder releases
    chunk.release();

    // then -- still held, not recycled
    assertThat(pool.pooledChunkCount()).isZero();

    // when -- the last holder releases
    chunk.release();

    // then -- recycled exactly now
    assertThat(pool.pooledChunkCount()).isEqualTo(1);
  }

  @Test
  void shouldResetBumpPointerOnReuse() {
    // given -- a chunk with appended bytes, fully released
    final ChunkPool pool = new ChunkPool(64, 4);
    final Chunk chunk = pool.acquire();
    chunk.append(new byte[17]);
    assertThat(chunk.remaining()).isEqualTo(64 - 17);
    chunk.release();

    // when -- the pool hands it out again
    final Chunk reused = pool.acquire();

    // then -- same chunk, fresh bump pointer
    assertThat(reused).isSameAs(chunk);
    assertThat(reused.remaining()).isEqualTo(64);
    reused.release();
  }

  @Test
  void shouldDropChunksBeyondThePoolBound() {
    // given -- more live chunks than the pool retains
    final ChunkPool pool = new ChunkPool(64, 2);
    final Chunk first = pool.acquire();
    final Chunk second = pool.acquire();
    final Chunk third = pool.acquire();

    // when -- all of them release
    first.release();
    second.release();
    third.release();

    // then -- the overflow chunk was dropped for the garbage collector, not retained
    assertThat(pool.pooledChunkCount()).isEqualTo(2);
  }

  @Test
  void shouldNeverPoolDedicatedChunks() {
    // given -- a dedicated (oversized-slice) chunk
    final ChunkPool pool = new ChunkPool(64, 4);
    final Chunk dedicated = pool.acquireDedicated(1024);
    assertThat(dedicated.isDedicated()).isTrue();
    assertThat(dedicated.remaining()).isEqualTo(1024);

    // when -- its last reference drops
    dedicated.release();

    // then -- it is abandoned, not pooled
    assertThat(pool.pooledChunkCount()).isZero();
  }

  @Test
  void shouldRejectReleaseBelowZeroAndRetainAfterZero() {
    // given -- a chunk released to zero (and thus recycled)
    final ChunkPool pool = new ChunkPool(64, 4);
    final Chunk chunk = pool.acquire();
    chunk.release();

    // when / then -- the recycled chunk's count was reset for the next acquirer, so misuse is
    // observable on a dedicated chunk, which is never reset
    final Chunk dedicated = pool.acquireDedicated(16);
    dedicated.release();
    assertThatThrownBy(dedicated::release).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(dedicated::retain).isInstanceOf(IllegalStateException.class);
    assertThat(pool.pooledChunkCount()).isEqualTo(1); // only the pooled chunk came back
  }

  @Test
  void shouldRotateWriterPastFullChunksAndDivertOversizedSlices() {
    // given -- a writer over 64-byte chunks (dedicated threshold: 16 bytes)
    final ChunkPool pool = new ChunkPool(64, 4);
    final ChunkWriter writer = new ChunkWriter(pool);

    // when -- appends fill the first chunk exactly and one slice exceeds the threshold
    final Chunk first = writer.chunkFor(16);
    first.append(new byte[16]);
    Chunk sameChunk = null;
    for (int i = 0; i < 3; i++) {
      sameChunk = writer.chunkFor(16);
      sameChunk.append(new byte[16]);
    }
    final Chunk dedicated = writer.chunkFor(17);
    final Chunk rotated = writer.chunkFor(16); // 0 remaining -> rotate

    // then -- small slices share the open chunk, oversized ones get dedicated chunks, and a slice
    // that no longer fits rotates to a fresh pooled chunk (releasing the writer's reference on the
    // full one — held by nobody else here, so it recycles)
    assertThat(sameChunk).isSameAs(first);
    assertThat(dedicated.isDedicated()).isTrue();
    assertThat(rotated).isNotSameAs(first);
    assertThat(rotated.isDedicated()).isFalse();
    assertThat(pool.pooledChunkCount()).isEqualTo(1);
    dedicated.release();
  }
}
