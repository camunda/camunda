/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.layered.LayeredStoreCoordinator.PersistRound;
import io.camunda.zeebe.db.layered.segment.ChunkPool;
import io.camunda.zeebe.db.layered.segment.ChunkWriter;
import io.camunda.zeebe.db.layered.util.InMemoryDurableState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The chunk lifetime rule end to end: a chunk slice is valid while a segment referencing the chunk
 * is retained; views retain their segments; a chunk returns to the pool only when every referencing
 * segment (and the writer still filling it) released. Uses tiny chunks so freezes rotate the writer
 * past chunks deterministically.
 */
final class ChunkReclamationTest {

  private static final String STORE = "cf";
  private static final int CHUNK_SIZE = 64;

  private final InMemoryDurableState state = new InMemoryDurableState();
  private final ChunkPool pool = new ChunkPool(CHUNK_SIZE, 8);
  private final ViewPublisher publisher = new ViewPublisher();

  private LayeredKeyValueStore newStore(final int pipelineSegmentLimit) {
    return new LayeredKeyValueStore(
        STORE,
        state.store(STORE),
        1024 * 1024,
        false,
        pipelineSegmentLimit,
        LayeredStoreMetrics.noop(),
        new ChunkWriter(pool));
  }

  private LayeredStoreCoordinator newCoordinator(final LayeredKeyValueStore store) {
    return new LayeredStoreCoordinator(
        List.of(store),
        state.sink(),
        state.snapshotSource(),
        publisher::publish,
        LayeredStoreMetrics.noop());
  }

  private static void runRound(final LayeredStoreCoordinator coordinator, final long watermark)
      throws Exception {
    final PersistRound round = coordinator.prepareRound(watermark);
    round.persist();
    coordinator.completeRound(round, true);
  }

  /** Freezes enough bytes to rotate the writer past at least one chunk. */
  private static void writeAndFreeze(
      final LayeredKeyValueStore store, final String keyPrefix, final long watermark) {
    for (int i = 0; i < 10; i++) {
      store.put(bytes(keyPrefix + "-" + i), bytes("value-" + i));
    }
    store.promote();
    store.freeze(watermark);
  }

  @Test
  void shouldKeepChunksAliveForViewHeldSegmentsAfterPersistDrop() throws Exception {
    // given -- frozen segments spanning several chunks, held by a reader's view
    final LayeredKeyValueStore store = newStore(10);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    writeAndFreeze(store, "a", 1);
    writeAndFreeze(store, "b", 2);
    coordinator.freezeAll(2); // republish so the acquired view holds the segments
    final ReadOnlyView view = publisher.acquireLatest();
    assertThat(pool.pooledChunkCount()).isZero();

    // when -- a successful round drops the drained segments from the pipeline
    runRound(coordinator, 3);

    // then -- the view still pins the segments, so no chunk recycled and reads stay exact
    assertThat(pool.pooledChunkCount()).isZero();
    assertThat(view.get(STORE, bytes("a-0"))).isEqualTo(bytes("value-0"));
    assertThat(view.get(STORE, bytes("b-9"))).isEqualTo(bytes("value-9"));

    // when -- the last holder releases the view
    publisher.release(view);

    // then -- the rotated-past chunks return to the pool (the writer's open chunk stays out)
    assertThat(pool.pooledChunkCount()).isPositive();
    coordinator.close();
  }

  @Test
  void shouldServeCorrectBytesAfterChunkReuse() throws Exception {
    // given -- a persisted-and-released first generation whose chunks recycled
    final LayeredKeyValueStore store = newStore(10);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    writeAndFreeze(store, "old", 1);
    runRound(coordinator, 2);
    final int recycled = pool.pooledChunkCount();
    assertThat(recycled).isPositive();

    // when -- new freezes reuse the recycled chunks
    writeAndFreeze(store, "new", 3);
    assertThat(pool.pooledChunkCount()).isLessThan(recycled);

    // then -- new entries read from the reused chunks, old entries via the durable store
    assertThat(store.get(bytes("new-5"))).isEqualTo(bytes("value-5"));
    assertThat(store.get(bytes("old-5"))).isEqualTo(bytes("value-5"));
    assertThat(state.committedValue(STORE, bytes("old-5"))).isEqualTo(bytes("value-5"));
    coordinator.close();
  }

  @Test
  void shouldShareChunksAcrossIndexOnlyMerges() throws Exception {
    // given -- a segment limit of 1, so the second freeze merges the pipeline
    final LayeredKeyValueStore store = newStore(1);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    writeAndFreeze(store, "a", 1);
    writeAndFreeze(store, "b", 2); // merges; inputs released, merged segment adopts their chunks

    // then -- the merge moved refs, not bytes: no chunk recycled, all entries readable
    assertThat(store.segmentsNewestFirst()).hasSize(1);
    assertThat(pool.pooledChunkCount()).isZero();
    assertThat(store.get(bytes("a-3"))).isEqualTo(bytes("value-3"));
    assertThat(store.get(bytes("b-7"))).isEqualTo(bytes("value-7"));

    // when -- a round drains the merged segment and nothing else holds it
    runRound(coordinator, 3);

    // then -- the shared chunks recycle now
    assertThat(pool.pooledChunkCount()).isPositive();
    coordinator.close();
  }

  @Test
  void shouldCarryOversizedValuesInDedicatedChunks() throws Exception {
    // given -- a value far above the dedicated threshold (a quarter chunk)
    final LayeredKeyValueStore store = newStore(10);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    final byte[] oversized = new byte[CHUNK_SIZE * 3];
    for (int i = 0; i < oversized.length; i++) {
      oversized[i] = (byte) i;
    }
    store.put(bytes("big"), oversized);
    store.promote();
    store.freeze(1);

    // then -- readable from its dedicated chunk
    assertThat(store.get(bytes("big"))).isEqualTo(oversized);

    // when -- persisted and retired
    runRound(coordinator, 2);

    // then -- the dedicated chunk never enters the pool, and the durable store holds the value
    assertThat(state.committedValue(STORE, bytes("big"))).isEqualTo(oversized);
    assertThat(store.get(bytes("big"))).isEqualTo(oversized);
    coordinator.close();
  }

  private static byte[] bytes(final String value) {
    return value.getBytes(UTF_8);
  }
}
