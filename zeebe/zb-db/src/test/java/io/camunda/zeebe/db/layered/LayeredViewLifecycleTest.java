/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.layered.LayeredStoreCoordinator.PersistRound;
import io.camunda.zeebe.db.layered.util.InMemoryDurableState;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

/**
 * Reference-counted view lifecycle: snapshots close exactly once, only after the coordinator's
 * rotation AND every reader holding the view released — multi-reader distribution through {@link
 * ViewPublisher}.
 */
final class LayeredViewLifecycleTest {

  private static final String STORE_A = "store-a";
  private static final long TIMEOUT_SECONDS = 30;

  private final InMemoryDurableState state = new InMemoryDurableState();
  private final TrackingSnapshotSource snapshots =
      new TrackingSnapshotSource(state.snapshotSource());
  private final ViewPublisher publisher = new ViewPublisher();

  private LayeredKeyValueStore newStore(final String name) {
    return new LayeredKeyValueStore(name, state.store(name), 1024 * 1024, false, 4);
  }

  private LayeredStoreCoordinator newCoordinator(final LayeredKeyValueStore... stores) {
    return new LayeredStoreCoordinator(
        List.of(stores), state.sink(), snapshots, publisher::publish, LayeredStoreMetrics.noop());
  }

  private void runRound(final LayeredStoreCoordinator coordinator, final long watermark)
      throws Exception {
    final PersistRound round = coordinator.prepareRound(watermark);
    round.persist();
    coordinator.completeRound(round, true);
  }

  @Test
  void shouldNotCloseOldSnapshotWhileReaderHoldsOldView() throws Exception {
    // given -- a reader holding the pre-round view
    final LayeredKeyValueStore store = newStore(STORE_A);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    final ReadOnlyView held = publisher.acquireLatest();
    store.put(bytes(1), bytes(10));
    store.promote();

    // when -- a successful round rotates the snapshot
    runRound(coordinator, 10);

    // then -- the pre-round snapshot stays open for the reader mid-scan
    assertThat(snapshots.closeCount(0)).isZero();
    assertThat(held.get(STORE_A, bytes(1))).isNull(); // still the old consistent cut

    // when -- the last holder releases
    publisher.release(held);

    // then -- closed exactly once
    assertThat(snapshots.closeCount(0)).isEqualTo(1);
    coordinator.close();
    snapshots.assertAllClosedExactlyOnce();
  }

  @Test
  void shouldCloseRotatedSnapshotImmediatelyWithoutReaders() throws Exception {
    // given
    final LayeredKeyValueStore store = newStore(STORE_A);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    store.put(bytes(1), bytes(10));
    store.promote();

    // when -- rotation with no reader holding the old view
    runRound(coordinator, 10);

    // then -- the coordinator's release was the last reference
    assertThat(snapshots.created()).isEqualTo(2);
    assertThat(snapshots.closeCount(0)).isEqualTo(1);
    assertThat(snapshots.closeCount(1)).isZero();
  }

  @Test
  void shouldKeepSharedSnapshotAliveAcrossFreezeRepublishGenerations() throws Exception {
    // given -- readers on two successive freeze generations sharing one snapshot
    final LayeredKeyValueStore store = newStore(STORE_A);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    final ReadOnlyView generation0 = publisher.acquireLatest();

    store.put(bytes(1), bytes(10));
    store.promote();
    coordinator.freezeAll(10);
    final ReadOnlyView generation1 = publisher.acquireLatest();
    assertThat(generation1).isNotSameAs(generation0);

    // then -- republish swapped views but kept the single snapshot open
    assertThat(snapshots.created()).isEqualTo(1);
    assertThat(snapshots.closeCount(0)).isZero();

    // when -- both readers release; the coordinator still holds the current view
    publisher.release(generation0);
    publisher.release(generation1);

    // then
    assertThat(snapshots.closeCount(0)).isZero();

    // when -- a round rotates to a fresh snapshot
    runRound(coordinator, 20);

    // then -- the pre-round snapshot closes exactly once, the fresh one stays open
    assertThat(snapshots.created()).isEqualTo(2);
    assertThat(snapshots.closeCount(0)).isEqualTo(1);
    assertThat(snapshots.closeCount(1)).isZero();
    coordinator.close();
    snapshots.assertAllClosedExactlyOnce();
  }

  @Test
  void shouldAcquireFreshViewAfterRotationAndRejectDoubleRelease() throws Exception {
    // given -- a view acquired before a rotation
    final LayeredKeyValueStore store = newStore(STORE_A);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    final ReadOnlyView first = publisher.acquireLatest();
    store.put(bytes(1), bytes(10));
    store.promote();

    // when -- rotation happens, then the reader releases and re-acquires
    runRound(coordinator, 10);
    publisher.release(first);
    final ReadOnlyView second = publisher.acquireLatest();

    // then -- the second acquisition is the post-round view, reading the fresh cut
    assertThat(second).isNotSameAs(first).isSameAs(coordinator.currentView());
    assertThat(second.get(STORE_A, bytes(1))).containsExactly(bytes(10));

    // then -- double release throws (releasing more than retained is a caller bug)
    assertThatThrownBy(first::release).isInstanceOf(IllegalStateException.class);
    // and a released-to-zero view cannot be resurrected
    assertThatThrownBy(first::retain).isInstanceOf(IllegalStateException.class);
    publisher.release(second);
  }

  @Test
  void shouldRejectAcquireBeforeFirstPublish() {
    // given
    final ViewPublisher empty = new ViewPublisher();

    // when / then
    assertThatThrownBy(empty::acquireLatest).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldReleaseCoordinatorReferenceOnCloseIdempotently() {
    // given
    final LayeredStoreCoordinator coordinator = newCoordinator(newStore(STORE_A));

    // when
    coordinator.close();
    coordinator.close();

    // then -- the initial snapshot closed exactly once, the view slot is gone
    assertThat(snapshots.created()).isEqualTo(1);
    assertThat(snapshots.closeCount(0)).isEqualTo(1);
    assertThat(coordinator.currentView()).isNull();
  }

  @Test
  void shouldCloseEverySnapshotExactlyOnceUnderConcurrentAcquireRelease() throws Exception {
    // given -- reader threads hammering acquire/read/release while the owner freezes and rounds
    final LayeredKeyValueStore store = newStore(STORE_A);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    final int readerCount = 4;
    final ExecutorService executor = Executors.newFixedThreadPool(readerCount);
    final AtomicBoolean stop = new AtomicBoolean();
    try {
      final List<Future<?>> readers = new ArrayList<>();
      for (int i = 0; i < readerCount; i++) {
        readers.add(
            executor.submit(
                () -> {
                  while (!stop.get()) {
                    final ReadOnlyView view = publisher.acquireLatest();
                    try {
                      view.get(STORE_A, bytes(1));
                      view.prefixScan(STORE_A, new byte[0], (key, value) -> {});
                    } finally {
                      publisher.release(view);
                    }
                  }
                  return null;
                }));
      }

      // when -- the owner thread interleaves writes, freezes and full persist rounds
      for (int i = 1; i <= 200; i++) {
        store.put(bytes(1), bytes(i & 0xFF));
        store.put(bytes(2, i & 0xFF), bytes(i & 0xFF));
        store.promote();
        if (i % 4 == 0) {
          runRound(coordinator, i);
        } else {
          coordinator.freezeAll(i);
        }
      }
      stop.set(true);

      // then -- every reader finished without an exception
      for (final Future<?> reader : readers) {
        reader.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      }
    } finally {
      stop.set(true);
      executor.shutdownNow();
      assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    // then -- after the owner releases its reference, every snapshot closed exactly once
    coordinator.close();
    assertThat(snapshots.created()).isEqualTo(51); // initial + one per round (200 / 4)
    snapshots.assertAllClosedExactlyOnce();
  }

  private static byte[] bytes(final int... values) {
    final byte[] result = new byte[values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = (byte) values[i];
    }
    return result;
  }

  /** Wraps every snapshot the source hands out to count closes, for exactly-once assertions. */
  private static final class TrackingSnapshotSource implements SnapshotSource {

    private final SnapshotSource delegate;
    private final List<AtomicInteger> closeCounts = new ArrayList<>();

    TrackingSnapshotSource(final SnapshotSource delegate) {
      this.delegate = delegate;
    }

    @Override
    public synchronized ReadSnapshot takeSnapshot() {
      final ReadSnapshot snapshot = delegate.takeSnapshot();
      final AtomicInteger closes = new AtomicInteger();
      closeCounts.add(closes);
      return new ReadSnapshot() {
        @Override
        public byte[] get(final String storeName, final byte[] key) {
          return snapshot.get(storeName, key);
        }

        @Override
        public void prefixScan(
            final String storeName, final byte[] prefix, final BiConsumer<byte[], byte[]> visitor) {
          snapshot.prefixScan(storeName, prefix, visitor);
        }

        @Override
        public void close() {
          closes.incrementAndGet();
          snapshot.close();
        }
      };
    }

    synchronized int created() {
      return closeCounts.size();
    }

    synchronized int closeCount(final int index) {
      return closeCounts.get(index).get();
    }

    synchronized void assertAllClosedExactlyOnce() {
      for (int i = 0; i < closeCounts.size(); i++) {
        assertThat(closeCounts.get(i)).as("snapshot %d must be closed exactly once", i).hasValue(1);
      }
    }
  }
}
