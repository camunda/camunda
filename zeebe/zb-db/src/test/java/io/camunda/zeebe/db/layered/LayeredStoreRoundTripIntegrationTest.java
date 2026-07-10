/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.layered.LayeredStoreCoordinator.PersistRound;
import io.camunda.zeebe.db.layered.rocksdb.RocksDbLayeredBacking;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Full round-trip of the layered store machinery over a real RocksDB backing. */
final class LayeredStoreRoundTripIntegrationTest {

  private static final String STORE_A = "store-a";
  private static final String STORE_B = "store-b";
  private static final long TIMEOUT_SECONDS = 30;

  private @TempDir Path tempDir;
  private RocksDbLayeredBacking backing;
  private ExecutorService executor;
  private LayeredStoreCoordinator coordinator;

  @BeforeEach
  void setUp() {
    backing = RocksDbLayeredBacking.open(tempDir, List.of(STORE_A, STORE_B));
    executor = Executors.newFixedThreadPool(2);
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
    if (coordinator != null) {
      // the coordinator never closes its latest view's snapshot; release the native handle
      // before the database goes away
      coordinator.currentView().snapshot().close();
    }
    if (backing != null) {
      backing.close();
    }
  }

  private LayeredKeyValueStore newStore(final String name) {
    return new LayeredKeyValueStore(name, backing.store(name), 1024 * 1024, false, 4);
  }

  @Test
  void shouldPersistRoundsIntoRocksDbAndAdvanceAnchor() throws Exception {
    // given -- two stores over one backing, with batches across several freezes
    final LayeredKeyValueStore storeA = newStore(STORE_A);
    final LayeredKeyValueStore storeB = newStore(STORE_B);
    coordinator =
        new LayeredStoreCoordinator(
            List.of(storeA, storeB), backing.sink(), backing.snapshotSource(), view -> {});

    storeA.put(bytes(1), bytes(10));
    storeB.put(bytes(2), bytes(20));
    storeA.promote();
    storeB.promote();
    coordinator.freezeAll(10);

    storeA.put(bytes(1), bytes(11)); // overwrites the version frozen at 10
    storeA.put(bytes(3), bytes(30));
    storeA.delete(bytes(3)); // annihilates within the same batch
    storeA.promote();
    coordinator.freezeAll(20);

    storeB.put(bytes(4), bytes(40)); // still active — prepareRound freezes it implicitly
    storeB.promote();

    // when -- the drain runs on an IO thread, completion on the owner (test) thread
    final PersistRound round = coordinator.prepareRound(25);
    runOnIoThread(round);
    coordinator.completeRound(round, true);

    // then -- RocksDB holds the merged final versions and the round's anchor
    assertThat(backing.store(STORE_A).get(bytes(1))).containsExactly(bytes(11));
    assertThat(backing.store(STORE_A).get(bytes(3))).isNull();
    assertThat(backing.store(STORE_B).get(bytes(2))).containsExactly(bytes(20));
    assertThat(backing.store(STORE_B).get(bytes(4))).containsExactly(bytes(40));
    assertThat(round.anchor()).isEqualTo(25);
    assertThat(backing.sink().readAnchor()).isEqualTo(25);

    // when -- a second round deletes a previously persisted key
    storeA.delete(bytes(1));
    storeA.promote();
    final PersistRound secondRound = coordinator.prepareRound(30);
    runOnIoThread(secondRound);
    coordinator.completeRound(secondRound, true);

    // then
    assertThat(backing.store(STORE_A).get(bytes(1))).isNull();
    assertThat(backing.sink().readAnchor()).isEqualTo(30);
    assertThat(coordinator.currentView().get(STORE_A, bytes(1))).isNull();
    assertThat(coordinator.currentView().get(STORE_B, bytes(4))).containsExactly(bytes(40));
  }

  @Test
  void shouldRecoverAnchorAndStateAfterReopen() throws Exception {
    // given -- a committed round
    final LayeredKeyValueStore storeA = newStore(STORE_A);
    coordinator =
        new LayeredStoreCoordinator(
            List.of(storeA), backing.sink(), backing.snapshotSource(), view -> {});
    storeA.put(bytes(1), bytes(10));
    storeA.promote();
    final PersistRound round = coordinator.prepareRound(42);
    runOnIoThread(round);
    coordinator.completeRound(round, true);

    // when -- the database is closed and reopened at the same path
    coordinator.currentView().snapshot().close();
    coordinator = null;
    backing.close();
    backing = RocksDbLayeredBacking.open(tempDir, List.of(STORE_A, STORE_B));

    // then -- recovery sees the last round's watermark and its state through a fresh store
    assertThat(backing.sink().readAnchor()).isEqualTo(42);
    assertThat(backing.store(STORE_A).get(bytes(1))).containsExactly(bytes(10));
    final LayeredKeyValueStore recovered = newStore(STORE_A);
    assertThat(recovered.get(bytes(1))).containsExactly(bytes(10));
  }

  @Test
  void shouldPublishStaleButNeverTornViewsToConcurrentReader() throws Exception {
    // given -- two keys always written together in one batch, plus a per-round
    // created-then-deleted key; a torn view would mix their generations
    final byte[] k1 = bytes(1);
    final byte[] k2 = bytes(2);
    final byte[] kTemp = bytes(3);
    final LayeredKeyValueStore storeA = newStore(STORE_A);
    final AtomicReference<ReadOnlyView> latestView = new AtomicReference<>();
    final Object handoff = new Object();
    // the lock mirrors the actor-message handoff: a reader never reads a view the rotation
    // already retired, because the swap and each read pass are mutually exclusive
    coordinator =
        new LayeredStoreCoordinator(
            List.of(storeA),
            backing.sink(),
            backing.snapshotSource(),
            view -> {
              synchronized (handoff) {
                latestView.set(view);
              }
            });
    final AtomicBoolean writerDone = new AtomicBoolean();

    // when -- the writer loops rounds while the reader continuously re-grabs the latest view
    final Future<?> writer =
        executor.submit(
            () -> {
              try {
                for (int i = 1; i <= 20; i++) {
                  storeA.put(k1, bytes(i));
                  storeA.put(k2, bytes(i));
                  storeA.put(kTemp, bytes(i));
                  storeA.promote();
                  coordinator.freezeAll(2L * i);

                  storeA.delete(kTemp);
                  storeA.promote();
                  coordinator.freezeAll(2L * i + 1);

                  final PersistRound round = coordinator.prepareRound(2L * i + 1);
                  round.persist();
                  coordinator.completeRound(round, true);
                }
                return null;
              } finally {
                writerDone.set(true);
              }
            });
    final Future<?> reader =
        executor.submit(
            () -> {
              while (!writerDone.get()) {
                synchronized (handoff) {
                  final ReadOnlyView view = latestView.get();
                  if (view == null) {
                    continue;
                  }
                  final byte[] v1 = view.get(STORE_A, k1);
                  final byte[] v2 = view.get(STORE_A, k2);
                  assertThat(v2)
                      .as("keys written in the same batch must show the same generation")
                      .isEqualTo(v1);
                  final byte[] vTemp = view.get(STORE_A, kTemp);
                  if (vTemp != null) {
                    assertThat(vTemp)
                        .as("a visible transient key must match its round's generation")
                        .isEqualTo(v1);
                  }
                }
              }
              return null;
            });

    // then -- both threads finish in bounded time without a consistency violation
    writer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    reader.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    final ReadOnlyView finalView = coordinator.currentView();
    assertThat(finalView.get(STORE_A, k1)).containsExactly(bytes(20));
    assertThat(finalView.get(STORE_A, k2)).containsExactly(bytes(20));
    assertThat(finalView.get(STORE_A, kTemp)).isNull();
    assertThat(backing.sink().readAnchor()).isEqualTo(41);
  }

  private void runOnIoThread(final PersistRound round) throws Exception {
    final Future<?> persisted =
        executor.submit(
            () -> {
              round.persist();
              return null;
            });
    persisted.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }

  private static byte[] bytes(final int... values) {
    final byte[] result = new byte[values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = (byte) values[i];
    }
    return result;
  }
}
